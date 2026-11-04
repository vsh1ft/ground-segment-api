package lt.vsh1ft.groundstation.catalog;

import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lt.vsh1ft.groundstation.elements.CelestrakException;
import lt.vsh1ft.groundstation.elements.OmmDownloader;
import lt.vsh1ft.groundstation.elements.OrbitDataUnavailableException;
import lt.vsh1ft.groundstation.elements.StoredOmm;
import lt.vsh1ft.groundstation.elements.StoredOmmRepository;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class SatelliteService {

    private static final Pattern NORAD_ID = Pattern.compile("\\d{1,9}");
    private static final Sort BY_NAME = Sort.by("name", "noradId");

    private final SatelliteRepository satellites;
    private final SatelliteSummaryRepository summaries;
    private final StoredOmmRepository elements;
    private final OmmDownloader downloader;
    private final JdbcAggregateOperations aggregates;
    private final TransactionOperations transactions;

    public SatelliteSummary get(NoradId satellite) {
        return summaries.findById(satellite.value())
                .orElseThrow(() -> new SatelliteNotInCatalogException(satellite));
    }

    public void ensureInCatalog(NoradId satellite) {
        if (!satellites.existsById(satellite.value())) {
            throw new SatelliteNotInCatalogException(satellite);
        }
    }

    public Page<SatelliteSummary> search(String term, Pageable page) {
        final var pageable = PageRequest.of(page.getPageNumber(), page.getPageSize(), BY_NAME);
        if (StringUtils.hasText(term)) {
            return matching(term.strip(), pageable);
        }
        return summaries.findAll(pageable);
    }

    public void add(NoradId satellite) {
        if (satellites.existsById(satellite.value())) {
            throw new SatelliteAlreadyInCatalogException(satellite);
        }
        final var omm = download(satellite);
        transactions.executeWithoutResult(_ -> {
            aggregates.insert(Satellite.from(omm));
            elements.save(omm);
        });
        log.info("Added {} ({}) to the catalog, epoch {}", omm.objectName(), satellite, omm.epoch());
    }

    @Transactional
    public void delete(NoradId satellite) {
        ensureInCatalog(satellite);
        satellites.deleteById(satellite.value());
    }

    private Page<SatelliteSummary> matching(String term, Pageable pageable) {
        if (NORAD_ID.matcher(term).matches()) {
            return summaries.findByNoradId(Integer.parseInt(term), pageable);
        }
        return summaries.findByNameContainingIgnoreCase(term, pageable);
    }

    private StoredOmm download(NoradId satellite) {
        try {
            return downloader.download(satellite);
        } catch (CelestrakException e) {
            throw new OrbitDataUnavailableException(
                    "Can't add NORAD ID " + satellite + " to the catalog: CelesTrak is unavailable", e);
        }
    }
}
