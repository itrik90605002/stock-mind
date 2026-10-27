package com.warehouse.assistant.plan;

import com.warehouse.config.AppProperties;
import com.warehouse.product.domain.Product;
import com.warehouse.product.domain.ProductRepository;
import com.warehouse.product.domain.ProductSpecifications;
import com.warehouse.purchasing.domain.PurchaseOrderStatus;
import com.warehouse.security.AuthUser;
import com.warehouse.stock.domain.MovementType;
import com.warehouse.supplier.domain.Supplier;
import com.warehouse.supplier.domain.SupplierRepository;
import com.warehouse.transfer.domain.TransferStatus;
import com.warehouse.warehouse.domain.Warehouse;
import com.warehouse.warehouse.domain.WarehouseRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns an untrusted {@link QueryPlan} into a {@link ValidatedPlan}, or rejects it.
 *
 * <ul>
 *   <li>Intent, movement type, status, direction and sort must be members of fixed enums.</li>
 *   <li>Product, warehouse, supplier and category text is only ever used as a lookup key (through
 *       parameterised repository queries) and resolved to ids/known values; it never reaches SQL text.</li>
 *   <li>The warehouse scope is always intersected with the user's assigned warehouses.</li>
 *   <li>Dates must parse and fall within a bounded window; limits are capped.</li>
 * </ul>
 */
@Component
public class PlanValidator {

    static final int DEFAULT_LIMIT = 25;
    static final int DEFAULT_HORIZON_DAYS = 7;
    static final int DEFAULT_WINDOW_DAYS = 30;
    private static final int MAX_TEXT = 100;
    private static final int MAX_RANGE_DAYS = 366;
    private static final int MAX_LOOKBACK_YEARS = 3;
    private static final int MAX_PRODUCT_MATCHES = 50;

    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final int maxRows;
    private final Clock clock;

    public PlanValidator(WarehouseRepository warehouseRepository, ProductRepository productRepository,
                         SupplierRepository supplierRepository, AppProperties properties, Clock clock) {
        this.warehouseRepository = warehouseRepository;
        this.productRepository = productRepository;
        this.supplierRepository = supplierRepository;
        this.maxRows = properties.ai().maxRows();
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ValidatedPlan validate(QueryPlan plan, AuthUser user) {
        QueryIntent intent = parseEnum(QueryIntent.class, plan.intent(), "question type");
        if (intent == null || intent == QueryIntent.UNSUPPORTED) {
            throw new PlanValidationException("That is not a question I can answer about inventory.");
        }
        LocalDate today = LocalDate.now(clock);

        // ---- warehouses (always within the user's permissions)
        List<Warehouse> accessible = warehouseRepository.findAllByOrderByCodeAsc().stream()
                .filter(w -> user.canAccess(w.getId())).toList();
        if (accessible.isEmpty()) {
            throw new PlanValidationException("You are not assigned to any warehouse.");
        }
        List<Long> warehouseIds;
        String warehouseLabel;
        String warehouseText = text(plan.warehouse(), "warehouse");
        if (warehouseText == null) {
            warehouseIds = accessible.stream().map(Warehouse::getId).toList();
            warehouseLabel = user.isAdmin() ? "all warehouses" : "your warehouses";
        } else {
            Warehouse match = resolveWarehouse(warehouseText);
            if (!user.canAccess(match.getId())) {
                throw new PlanValidationException("You do not have access to warehouse " + match.getCode() + ".");
            }
            warehouseIds = List.of(match.getId());
            warehouseLabel = match.getCode() + " " + match.getName();
        }

        // ---- products / category / supplier
        List<Long> productIds = List.of();
        String productLabel = null;
        String productText = text(plan.product(), "product");
        if (productText != null) {
            productIds = resolveProducts(productText);
            productLabel = productIds.size() == 1 ? describeProduct(productIds.getFirst()) : "\"" + productText + "\"";
        }
        String category = resolveCategory(text(plan.category(), "category"));
        Long supplierId = null;
        String supplierLabel = null;
        String supplierText = text(plan.supplier(), "supplier");
        if (supplierText != null) {
            Supplier supplier = resolveSupplier(supplierText);
            supplierId = supplier.getId();
            supplierLabel = supplier.getName();
        }

        // ---- enums
        MovementType movementType = parseEnum(MovementType.class, plan.movementType(), "movement type");
        ValidatedPlan.Direction direction = Optional.ofNullable(
                parseEnum(ValidatedPlan.Direction.class, plan.direction(), "direction")).orElse(ValidatedPlan.Direction.FAST);
        ValidatedPlan.SortBy sortBy = Optional.ofNullable(
                parseEnum(ValidatedPlan.SortBy.class, plan.sortBy(), "sort order")).orElse(ValidatedPlan.SortBy.RECENT);
        List<String> statuses = resolveStatuses(intent, plan.status());
        String statusLabel = plan.status() == null || plan.status().isBlank() ? null
                : plan.status().trim().toUpperCase(Locale.ROOT).replace(' ', '_');

        // ---- dates
        LocalDate from = parseDate(plan.fromDate(), "start date");
        LocalDate to = parseDate(plan.toDate(), "end date");
        boolean windowed = intent == QueryIntent.MOVEMENT_HISTORY || intent == QueryIntent.TOP_MOVERS;
        if (windowed && from == null && to == null) {
            from = today.minusDays(DEFAULT_WINDOW_DAYS - 1L);
            to = today;
        }
        if (from != null && to == null) {
            to = today;
        }
        if (to != null && from == null) {
            from = to.minusDays(DEFAULT_WINDOW_DAYS - 1L);
        }
        if (from != null) {
            if (to.isAfter(today)) {
                to = today;
            }
            if (from.isAfter(to)) {
                throw new PlanValidationException("The start date is after the end date.");
            }
            if (from.isBefore(today.minusYears(MAX_LOOKBACK_YEARS))) {
                throw new PlanValidationException("Dates can go back at most " + MAX_LOOKBACK_YEARS + " years.");
            }
            if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
                throw new PlanValidationException("Please ask about a period of at most one year.");
            }
        }

        // ---- numbers
        int horizon = plan.horizonDays() == null ? DEFAULT_HORIZON_DAYS : plan.horizonDays();
        if (horizon < 1 || horizon > 90) {
            throw new PlanValidationException("Forecasts can look 1 to 90 days ahead.");
        }
        int defaultLimit = intent == QueryIntent.TOP_MOVERS ? 10 : DEFAULT_LIMIT;
        int limit = plan.limit() == null ? defaultLimit : Math.max(1, Math.min(plan.limit(), maxRows));

        return new ValidatedPlan(intent, warehouseIds, warehouseLabel, productIds, productLabel, category, supplierId,
                supplierLabel, movementType, statuses, statusLabel, from, to, horizon, direction, sortBy, limit);
    }

    /** Human-readable summary of what will be queried, shown next to the answer. */
    public static String interpret(ValidatedPlan p) {
        StringBuilder sb = new StringBuilder(p.intent().label());
        switch (p.intent()) {
            case STOCKOUT_FORECAST -> sb.append(" · next ").append(p.horizonDays()).append(" days");
            case TOP_MOVERS -> sb.append(p.direction() == ValidatedPlan.Direction.FAST ? " · fastest" : " · slowest");
            case MOVEMENT_HISTORY -> {
                if (p.movementType() != null) {
                    sb.append(" · ").append(p.movementType().name().toLowerCase(Locale.ROOT).replace('_', ' '));
                }
                if (p.sortBy() == ValidatedPlan.SortBy.LARGEST) {
                    sb.append(" · largest first");
                }
            }
            default -> { }
        }
        sb.append(" · ").append(p.warehouseLabel());
        if (p.productLabel() != null) {
            sb.append(" · product ").append(p.productLabel());
        }
        if (p.category() != null) {
            sb.append(" · category ").append(p.category());
        }
        if (p.supplierLabel() != null) {
            sb.append(" · supplier ").append(p.supplierLabel());
        }
        if (p.statusLabel() != null) {
            sb.append(" · status ").append(p.statusLabel().toLowerCase(Locale.ROOT).replace('_', ' '));
        }
        if (p.from() != null) {
            sb.append(" · ").append(p.from()).append(" to ").append(p.to());
        }
        return sb.toString();
    }

    private Warehouse resolveWarehouse(String text) {
        List<Warehouse> all = warehouseRepository.findAllByOrderByCodeAsc();
        return bestMatch(all, text, Warehouse::getCode, Warehouse::getName)
                .orElseThrow(() -> new PlanValidationException("I could not find a warehouse called \"" + text + "\"."));
    }

    private List<Long> resolveProducts(String text) {
        Optional<Product> bySku = productRepository.findBySku(Product.normalizeSku(text));
        if (bySku.isPresent()) {
            return List.of(bySku.get().getId());
        }
        Specification<Product> spec = Specification.allOf(ProductSpecifications.matches(text),
                ProductSpecifications.archived(false));
        List<Long> ids = productRepository.findAll(spec, PageRequest.of(0, MAX_PRODUCT_MATCHES, Sort.by("sku")))
                .map(Product::getId).getContent();
        if (ids.isEmpty()) {
            throw new PlanValidationException("No product matches \"" + text + "\".");
        }
        return ids;
    }

    private String describeProduct(Long id) {
        return productRepository.findById(id).map(p -> p.getSku() + " " + p.getName()).orElse("#" + id);
    }

    private String resolveCategory(String text) {
        if (text == null) {
            return null;
        }
        List<String> categories = productRepository.findCategories();
        return bestMatch(categories, text, Function.identity(), Function.identity())
                .orElseThrow(() -> new PlanValidationException("Unknown category \"" + text + "\". Categories: "
                        + String.join(", ", categories) + "."));
    }

    private Supplier resolveSupplier(String text) {
        return bestMatch(supplierRepository.findAllByOrderByNameAsc(), text, Supplier::getName, Supplier::getName)
                .orElseThrow(() -> new PlanValidationException("I could not find a supplier called \"" + text + "\"."));
    }

    /** Exact (case-insensitive) key match, then exact name, then a unique "contains" match on the name. */
    private static <T> Optional<T> bestMatch(List<T> items, String text, Function<T, String> key,
                                             Function<T, String> name) {
        String needle = text.trim().toLowerCase(Locale.ROOT);
        for (T item : items) {
            if (key.apply(item).equalsIgnoreCase(needle) || name.apply(item).equalsIgnoreCase(needle)) {
                return Optional.of(item);
            }
        }
        List<T> partial = items.stream()
                .filter(i -> name.apply(i).toLowerCase(Locale.ROOT).contains(needle)
                        || needle.contains(key.apply(i).toLowerCase(Locale.ROOT)))
                .toList();
        if (partial.size() > 1) {
            throw new PlanValidationException("\"" + text + "\" matches several: "
                    + partial.stream().map(name).limit(5).collect(Collectors.joining(", ")) + ". Please be more specific.");
        }
        return partial.stream().findFirst();
    }

    private static List<String> resolveStatuses(QueryIntent intent, String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String status = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (intent == QueryIntent.PURCHASE_ORDER_STATUS) {
            if (status.equals("OPEN")) {
                return PurchaseOrderStatus.OPEN.stream().map(Enum::name).toList();
            }
            return List.of(parseEnum(PurchaseOrderStatus.class, status, "purchase order status").name());
        }
        if (intent == QueryIntent.TRANSFER_STATUS) {
            if (status.equals("OPEN")) {
                return EnumSet.of(TransferStatus.DRAFT, TransferStatus.DISPATCHED).stream().map(Enum::name).toList();
            }
            if (status.equals("IN_TRANSIT")) {
                return List.of(TransferStatus.DISPATCHED.name());
            }
            return List.of(parseEnum(TransferStatus.class, status, "transfer status").name());
        }
        return List.of();
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String what) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        try {
            return Enum.valueOf(type, normalized);
        } catch (IllegalArgumentException e) {
            throw new PlanValidationException("Unknown " + what + " \"" + raw + "\". Allowed: "
                    + Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", ")) + ".");
        }
    }

    private static LocalDate parseDate(String raw, String what) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new PlanValidationException("The " + what + " \"" + raw + "\" is not a valid date.");
        }
    }

    private static String text(String raw, String what) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > MAX_TEXT) {
            throw new PlanValidationException("The " + what + " filter is too long.");
        }
        return trimmed;
    }
}
