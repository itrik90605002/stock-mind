package com.warehouse.product.service;

import com.warehouse.common.exception.BusinessRuleException;
import com.warehouse.common.exception.NotFoundException;
import com.warehouse.common.web.PageResponse;
import com.warehouse.product.domain.Product;
import com.warehouse.product.domain.ProductRepository;
import com.warehouse.product.dto.ProductRequest;
import com.warehouse.product.dto.ProductResponse;
import com.warehouse.supplier.domain.Supplier;
import com.warehouse.supplier.service.SupplierService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

import static com.warehouse.product.domain.ProductSpecifications.archived;
import static com.warehouse.product.domain.ProductSpecifications.fromSupplier;
import static com.warehouse.product.domain.ProductSpecifications.inCategory;
import static com.warehouse.product.domain.ProductSpecifications.matches;

@Service
public class ProductService {

    private final ProductRepository repository;
    private final SupplierService supplierService;
    private final Clock clock;

    public ProductService(ProductRepository repository, SupplierService supplierService, Clock clock) {
        this.repository = repository;
        this.supplierService = supplierService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String q, String category, Long supplierId, boolean includeArchived,
                                                int page, int size) {
        Specification<Product> spec = Specification.allOf(matches(q), inCategory(category), fromSupplier(supplierId),
                archived(includeArchived));
        PageRequest pageable = PageRequest.of(page, size, Sort.by("sku"));
        return PageResponse.from(repository.findAll(spec, pageable), ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return ProductResponse.from(repository.findWithSupplierById(id)
                .orElseThrow(() -> NotFoundException.of("Product", id)));
    }

    @Transactional(readOnly = true)
    public List<String> categories() {
        return repository.findCategories();
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        String sku = Product.normalizeSku(request.sku());
        if (repository.existsBySku(sku)) {
            throw new BusinessRuleException("SKU_EXISTS", "A product with SKU " + sku + " already exists");
        }
        Product product = new Product(sku, request.name(), request.category(), request.unit(), request.unitCost(),
                blankToNull(request.description()), supplier(request.supplierId()), clock.instant());
        return ProductResponse.from(repository.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = find(id);
        if (!product.getSku().equals(Product.normalizeSku(request.sku()))) {
            throw new BusinessRuleException("SKU_IMMUTABLE", "The SKU of an existing product cannot be changed");
        }
        product.update(request.name(), request.category(), request.unit(), request.unitCost(),
                blankToNull(request.description()), supplier(request.supplierId()), clock.instant());
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse setArchived(Long id, boolean archived) {
        Product product = find(id);
        if (archived) {
            product.archive(clock.instant());
        } else {
            product.restore(clock.instant());
        }
        return ProductResponse.from(product);
    }

    public Product find(Long id) {
        return repository.findWithSupplierById(id).orElseThrow(() -> NotFoundException.of("Product", id));
    }

    private Supplier supplier(Long supplierId) {
        return supplierId == null ? null : supplierService.find(supplierId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
