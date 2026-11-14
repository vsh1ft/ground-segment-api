import { api } from '../../api.js';
import { dateTime, number } from '../../format.js';
import { card, emptyState, numeric, numericHeading, row, showingErrors, table } from '../../ui.js';

const COLUMNS = ['Pass', ...['Received', 'Lost', 'Corrupted', 'Loss'].map(numericHeading)];

export async function renderLinkReports(container, noradId) {
    const list = card();
    container.append(list);
    await showingErrors(list, async () => {
        const reports = (await api.linkReports(noradId)).reports;
        list.append(reports.length === 0
            ? emptyState('No link reports. They come from the simulator\'s live frame link, one per pass.')
            : table(COLUMNS, reports.map(report => row({},
                dateTime(report.passStart),
                numeric(report.framesReceived),
                numeric(report.framesLost),
                numeric(report.framesFailedCheck),
                numeric(`${number(report.lossRatio * 100, 1)} %`)))));
    });
}
