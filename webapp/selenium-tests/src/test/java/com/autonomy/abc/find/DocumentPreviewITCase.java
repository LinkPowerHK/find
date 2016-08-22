package com.autonomy.abc.find;

import com.autonomy.abc.base.FindTestBase;
import com.autonomy.abc.selenium.find.FindPage;
import com.autonomy.abc.selenium.find.FindService;
import com.autonomy.abc.selenium.find.IdolFindPage;
import com.autonomy.abc.selenium.find.filters.FilterPanel;
import com.autonomy.abc.selenium.find.preview.DetailedPreviewPage;
import com.autonomy.abc.selenium.find.preview.InlinePreview;
import com.autonomy.abc.selenium.find.results.FindResult;
import com.autonomy.abc.selenium.find.results.ResultsView;
import com.autonomy.abc.selenium.query.IndexFilter;
import com.hp.autonomy.frontend.selenium.application.ApplicationType;
import com.hp.autonomy.frontend.selenium.config.Browser;
import com.hp.autonomy.frontend.selenium.config.TestConfig;
import com.hp.autonomy.frontend.selenium.control.Frame;
import com.hp.autonomy.frontend.selenium.control.Session;
import com.hp.autonomy.frontend.selenium.control.Window;
import com.hp.autonomy.frontend.selenium.framework.logging.ActiveBug;
import com.hp.autonomy.frontend.selenium.framework.logging.ResolvedBug;
import com.hp.autonomy.frontend.selenium.util.Waits;
import org.junit.Before;
import org.junit.Test;

import static com.hp.autonomy.frontend.selenium.framework.state.TestStateAssert.assertThat;
import static com.hp.autonomy.frontend.selenium.framework.state.TestStateAssert.verifyThat;
import static com.hp.autonomy.frontend.selenium.matchers.StringMatchers.containsString;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.text.IsEmptyString.isEmptyOrNullString;
import static org.openqa.selenium.lift.Matchers.displayed;

public class DocumentPreviewITCase extends FindTestBase {
    private FindPage findPage;
    private FindService findService;

    public DocumentPreviewITCase(final TestConfig config) {
        super(config);
    }

    @Before
    public void setUp() {
        findPage = getElementFactory().getFindPage();
        findService = getApplication().findService();
        if(!isHosted()) {
            ((IdolFindPage) findPage).goToListView();
        }
    }

    @Test
    public void testShowDocumentPreview() {
        final ResultsView results = findService.search("cake");
        findPage.filterBy(new IndexFilter(filters().getIndex(1)));

        final InlinePreview docPreview = results.searchResult(1).openDocumentPreview();

        if (docPreview.loadingIndicatorExists()) {
            assertThat("Preview not stuck loading", docPreview.loadingIndicator(), not(displayed()));
        }

        assertThat("Index displayed", docPreview.getIndexName(), not(isEmptyOrNullString()));
        assertThat("Reference displayed", docPreview.getReference(), not(isEmptyOrNullString()));

        final Frame previewFrame = new Frame(getWindow(), docPreview.frame());
        final String frameText = previewFrame.getText();

        verifyThat("Preview document has content", frameText, not(isEmptyOrNullString()));
        assertThat("Preview document has no error", previewFrame.getText(), not(containsString("encountered an error")));

        docPreview.close();
    }

    @Test
    @ResolvedBug("FIND-497")
    public void testOpenOriginalDocInNewTab() {
        final Session session = getMainSession();

        final ResultsView results = findService.search("flail");
        if (isHosted()) {
            // e.g. FIFA contains links that redirect to new pages
            findPage.filterBy(new IndexFilter("simpsonsarchive"));
        }

        results.waitForResultsToLoad();

        for (final FindResult queryResult : results.getResults(4)) {
            final InlinePreview docViewer = queryResult.openDocumentPreview();

            final String reference = docViewer.getReference();
            final DetailedPreviewPage detailedPreviewPage = docViewer.openPreview();

            final Window original = session.getActiveWindow();

            assertThat("Link does not contain 'undefined'",detailedPreviewPage.originalDocLink(),not(containsString("undefined")));

            detailedPreviewPage.openOriginalDoc();
            final Window newWindow = session.switchWindow(session.countWindows() - 1);
            newWindow.activate();
            Waits.loadOrFadeWait();
            verifyThat(session.getDriver().getCurrentUrl(), containsString(reformatReference(reference)));

            newWindow.close();
            original.activate();

            detailedPreviewPage.goBackToSearch();
        }
    }

    private String reformatReference(final String badFormatReference) {
        return badFormatReference.replace(" ", "_").split("://")[1];
    }

    @Test
    public void testDetailedPreview() {
        final ResultsView results = findService.search("tragic");
        findPage.filterBy(new IndexFilter(filters().getIndex(1)));

        InlinePreview inlinePreview = results.getResult(1).openDocumentPreview();
        final DetailedPreviewPage detailedPreviewPage = inlinePreview.openPreview();

        //loading
        final String frameText = new Frame(getMainSession().getActiveWindow(), detailedPreviewPage.frame()).getText();
        verifyThat("Frame has content", frameText, not(isEmptyOrNullString()));
        verifyThat("Preview frame has no error", frameText, not(containsString("encountered an error")));

        checkHasMetaDataFields(detailedPreviewPage);

        checkSimilarDocuments(detailedPreviewPage);

        if(!isHosted()) {
            checkSimilarDates(detailedPreviewPage);
        }
        detailedPreviewPage.goBackToSearch();

    }

    private void checkHasMetaDataFields(final DetailedPreviewPage detailedPreviewPage) {
        verifyThat("Tab loads", !detailedPreviewPage.loadingIndicator().isDisplayed());
        verifyThat("Detailed Preview has reference", detailedPreviewPage.getReference(), not(nullValue()));
        if (isHosted()) {
            verifyThat("Detailed Preview has index", detailedPreviewPage.getIndex(), not(nullValue()));
        } else {
            verifyThat("Detailed Preview has database", detailedPreviewPage.getDatabase(), not(nullValue()));
        }
        verifyThat("Detailed Preview has title", detailedPreviewPage.getTitle(), not(nullValue()));
        verifyThat("Detailed Preview has summary", detailedPreviewPage.getSummary(), not(nullValue()));
//        verifyThat("Detailed Preview has date",detailedPreviewPage.getDate(),not(nullValue()));
    }

    private void checkSimilarDocuments(final DetailedPreviewPage detailedPreviewPage) {
        detailedPreviewPage.similarDocsTab().click();
        verifyThat("Tab loads", !detailedPreviewPage.loadingIndicator().isDisplayed());
    }

    private void checkSimilarDates(final DetailedPreviewPage detailedPreviewPage) {
        detailedPreviewPage.similarDatesTab().click();
        verifyThat("Tab loads", !detailedPreviewPage.loadingIndicator().isDisplayed());
        changeDateSliderToYearBefore(detailedPreviewPage);
        verifyThat("Can change to similar docs from year before", detailedPreviewPage.getSimilarDatesSummary(), containsString("Between 1 year"));
    }

    private void changeDateSliderToYearBefore(final DetailedPreviewPage detailedPreviewPage) {
        detailedPreviewPage.ithTick(1).click();
    }

    @Test
    @ActiveBug(value = "FIND-86", browsers = Browser.FIREFOX)
    public void testOneCopyOfDocInDetailedPreview() {
        final ResultsView results = findService.search("face");
        InlinePreview inlinePreview = results.getResult(1).openDocumentPreview();

        final DetailedPreviewPage detailedPreviewPage = inlinePreview.openPreview();

        verifyThat("Only 1 copy of that document in detailed preview", detailedPreviewPage.numberOfHeadersWithDocTitle(), lessThanOrEqualTo(1));

        detailedPreviewPage.goBackToSearch();

    }

    private FilterPanel filters() {
        return getElementFactory().getFilterPanel();
    }
}

