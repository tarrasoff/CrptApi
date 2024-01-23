package crptApi;

import com.google.common.util.concurrent.RateLimiter;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
@EnableRetry
public class CrptApi {

    public static void main(String[] args) {
        SpringApplication.run(CrptApi.class, args);
    }

    @Component
    @Data
    static class RateAccessLimiter {
        private int requestLimit;
        private int interval;
        private TimeUnit timeUnit;
        private RateLimiter rateLimiter;

        @Autowired
        public RateAccessLimiter(
                @Value("${spring.rateLimiter.requestLimit}") int requestLimit,
                @Value("${spring.rateLimiter.interval}") int interval,
                @Value("${spring.rateLimiter.timeUnit}") String timeUnit) {
            this.requestLimit = requestLimit;
            this.interval = interval;
            this.timeUnit = (timeUnit != null) ? TimeUnit.valueOf(timeUnit) : TimeUnit.SECONDS;
            this.rateLimiter = RateLimiter.create(requestLimit, interval, this.timeUnit);
        }

        public boolean checkAvailableRate() {
            return rateLimiter.tryAcquire();
        }
    }

    @RestController
    @RequiredArgsConstructor
    @RequestMapping("/api/v3/lk/documents")
    static class DocumentController {
        private final DocumentService documentService;

        @PostMapping("/create")
        public DocumentDto document(@Valid @RequestBody DocumentDto documentDto) {
            return documentService.createDocument(documentDto);
        }
    }

    @Service
    @RequiredArgsConstructor
    static class DocumentService {
        private final DocumentRepository documentRepository;
        private final DocumentMapper documentMapper;
        private final RateAccessLimiter rateAccessLimiter;

        @Transactional
        @Retryable(maxAttempts = 5, backoff = @Backoff(delay = 5), retryFor = RateLimitExceededException.class)
        public DocumentDto createDocument(DocumentDto documentDto) {
            if (!rateAccessLimiter.checkAvailableRate()) {
                throw new RateLimitExceededException(
                        String.format("Exceeded the rate limit in %s", rateAccessLimiter.getTimeUnit()));
            }
            Document newDocument = documentMapper.toEntity(documentDto);
            newDocument = documentRepository.save(newDocument);
            return documentMapper.toDto(newDocument);
        }
    }

    @Repository
    public interface DocumentRepository extends JpaRepository<Document, Long> {
    }

    @Mapper(componentModel = "spring",
            injectionStrategy = InjectionStrategy.FIELD,
            unmappedTargetPolicy = ReportingPolicy.IGNORE)
    public interface DocumentMapper {
        DocumentDto toDto(Document document);

        Document toEntity(DocumentDto documentDto);
    }

    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String message) {
            super(message);
        }
    }

    @Entity
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Table(name = "document")
    static class Document {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long doc_id;

        @OneToOne(mappedBy = "document")
        private Description description;

        @Column(name = "doc_status")
        private String doc_status;

        @Column(name = "doc_type")
        @Enumerated(EnumType.STRING)
        private DocumentType doc_type;

        @Column(name = "import_request")
        private boolean importRequest;

        @Column(name = "owner_inn")
        private String owner_inn;

        @Column(name = "participant_inn")
        private String participant_inn;

        @Column(name = "producer_inn")
        private String producer_inn;

        @CreationTimestamp
        @Temporal(TemporalType.TIMESTAMP)
        @Column(name = "production_date", nullable = false)
        private LocalDateTime production_date;

        @Column(name = "production_type")
        private String production_type;

        @OneToMany(mappedBy = "document")
        private List<Product> products;

        @CreationTimestamp
        @Temporal(TemporalType.TIMESTAMP)
        @Column(name = "reg_date", nullable = false)
        private LocalDateTime reg_date;

        @Column(name = "reg_number")
        private String reg_number;
    }

    @Entity
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Table(name = "description")
    static class Description {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "participant_inn", nullable = false)
        private String participantInn;

        @OneToOne
        @JoinColumn(name = "doc_id")
        private Document document;
    }

    @Entity
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Table(name = "product")
    static class Product {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "certificate_document")
        private String certificate_document;

        @CreationTimestamp
        @Temporal(TemporalType.TIMESTAMP)
        @Column(name = "certificate_document_date", nullable = false)
        private LocalDateTime certificate_document_date;

        @Column(name = "certificate_document_number")
        private String certificate_document_number;

        @Column(name = "owner_inn")
        private String owner_inn;

        @Column(name = "producer_inn")
        private String producer_inn;

        @CreationTimestamp
        @Temporal(TemporalType.TIMESTAMP)
        @Column(name = "production_date", nullable = false)
        private LocalDateTime production_date;

        @Column(name = "tnved_code")
        private String tnved_code;

        @Column(name = "uit_code")
        private String uit_code;

        @Column(name = "uitu_code")
        private String uitu_code;
        @ManyToOne
        @JoinColumn(name = "doc_id", nullable = false)
        private Document document;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    static class DocumentDto {
        private Long description_id;
        private Long doc_id;
        private String doc_status;
        private DocumentType doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private LocalDateTime production_date;
        private String production_type;
        private Long product_id;
        private LocalDateTime reg_date;
        private String reg_number;
    }

    enum DocumentType {
        LP_INTRODUCE_GOODS
    }
}