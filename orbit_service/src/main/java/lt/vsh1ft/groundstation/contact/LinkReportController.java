package lt.vsh1ft.groundstation.contact;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class LinkReportController implements LinkReportsApi {

    private final LinkReportArchive archive;

    @Override
    public LinkReportsResponse reports(int noradId, LinkReportsQuery query) {
        return new LinkReportsResponse(archive.newestFirst(new NoradId(noradId), query.limit()).stream()
                .map(LinkReportResponse::of).toList());
    }
}
