package lt.vsh1ft.groundstation.contact;

import java.util.List;

public record LinkReportsResponse(List<LinkReportResponse> reports) {

    public LinkReportsResponse {
        reports = List.copyOf(reports);
    }
}
