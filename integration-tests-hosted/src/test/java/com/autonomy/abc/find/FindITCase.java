package com.autonomy.abc.find;

import com.autonomy.abc.config.HostedTestBase;
import com.autonomy.abc.config.TestConfig;
import com.autonomy.abc.framework.KnownBug;
import com.autonomy.abc.framework.RelatedTo;
import com.autonomy.abc.selenium.application.HSODFind;
import com.autonomy.abc.selenium.control.Session;
import com.autonomy.abc.selenium.control.Window;
import com.autonomy.abc.selenium.element.FindParametricCheckbox;
import com.autonomy.abc.selenium.find.FindPage;
import com.autonomy.abc.selenium.find.FindResultsPage;
import com.autonomy.abc.selenium.indexes.Index;
import com.autonomy.abc.selenium.keywords.KeywordFilter;
import com.autonomy.abc.selenium.keywords.KeywordService;
import com.autonomy.abc.selenium.language.Language;
import com.autonomy.abc.selenium.navigation.HSODFindElementFactory;
import com.autonomy.abc.selenium.page.search.DocumentViewer;
import com.autonomy.abc.selenium.page.search.SearchBase;
import com.autonomy.abc.selenium.page.search.SearchPage;
import com.autonomy.abc.selenium.promotions.*;
import com.autonomy.abc.selenium.search.*;
import com.autonomy.abc.selenium.util.Errors;
import com.autonomy.abc.selenium.util.Locator;
import com.autonomy.abc.selenium.util.Waits;
import com.hp.autonomy.hod.client.api.authentication.ApiKey;
import com.hp.autonomy.hod.client.api.authentication.AuthenticationService;
import com.hp.autonomy.hod.client.api.authentication.AuthenticationServiceImpl;
import com.hp.autonomy.hod.client.api.authentication.TokenType;
import com.hp.autonomy.hod.client.api.resource.ResourceIdentifier;
import com.hp.autonomy.hod.client.api.textindex.query.fields.*;
import com.hp.autonomy.hod.client.config.HodServiceConfig;
import com.hp.autonomy.hod.client.error.HodErrorException;
import com.hp.autonomy.hod.client.token.TokenProxy;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.interactions.Actions;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.autonomy.abc.framework.ABCAssert.assertThat;
import static com.autonomy.abc.framework.ABCAssert.verifyThat;
import static com.autonomy.abc.matchers.ElementMatchers.*;
import static com.thoughtworks.selenium.SeleneseTestBase.assertTrue;
import static com.thoughtworks.selenium.SeleneseTestBase.fail;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Every.everyItem;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsCollectionContaining.hasItems;
import static org.openqa.selenium.lift.Matchers.displayed;

public class FindITCase extends HostedTestBase {
    private FindPage findPage;
    private FindResultsPage results;
    private final Matcher<String> noDocs = containsString(Errors.Search.NO_RESULTS);
    private PromotionService<?> promotionService;
    private KeywordService keywordService;
    private Window searchWindow;
    private Window findWindow;
    private HSODFind findApp;

    public FindITCase(TestConfig config) {
        super(config);
    }

    @Before
    public void setUp(){
        promotionService = getApplication().promotionService();
        keywordService = getApplication().keywordService();

        searchWindow = getMainSession().getActiveWindow();
        findWindow = getMainSession().openWindow(config.getFindUrl());
        findApp = new HSODFind(findWindow);
        findPage = findApp.elementFactory().getFindPage();
        results = findPage.getResultsPage();
    }

    @Test
    public void testSendKeys() throws InterruptedException {
        String searchTerm = "Fred is a chimpanzee";
        findPage.search(searchTerm);
        assertThat(findPage.getSearchBoxTerm(), is(searchTerm));
        assertThat(results.getText().toLowerCase(), not(containsString("error")));
    }

    @Test
    public void testPdfContentTypeValue(){
        findPage.search("red star");
        findPage.filterBy(new ParametricFilter("Content Type", "APPLICATION/PDF"));
        for(String type : results.getDisplayedDocumentsDocumentTypes()){
            assertThat(type,containsString("pdf"));
        }
    }

    @Test
    public void testHtmlContentTypeValue(){
        findPage.search("red star");
        findPage.filterBy(new ParametricFilter("Content Type", "TEXT/HTML"));
        for(String type : results.getDisplayedDocumentsDocumentTypes()){
            assertThat(type,containsString("html"));
        }
    }

    @Test
    public void testFilteringByParametricValues(){
        findPage.search("Alexis");
        findPage.waitForParametricValuesToLoad();

        int expectedResults = plainTextCheckbox().getResultsCount();
        plainTextCheckbox().check();
        results.waitForSearchLoadIndicatorToDisappear(FindResultsPage.Container.MIDDLE);
        verifyParametricFields(plainTextCheckbox(), expectedResults);
        verifyTicks(true, false);

        expectedResults = plainTextCheckbox().getResultsCount();
        simpsonsArchiveCheckbox().check();
        results.waitForSearchLoadIndicatorToDisappear(FindResultsPage.Container.MIDDLE);
        verifyParametricFields(plainTextCheckbox(), expectedResults);	//TODO Maybe change plainTextCheckbox to whichever has the higher value??
        verifyTicks(true, true);

        plainTextCheckbox().uncheck();
        results.waitForSearchLoadIndicatorToDisappear(FindResultsPage.Container.MIDDLE);
        expectedResults = simpsonsArchiveCheckbox().getResultsCount();
        verifyParametricFields(simpsonsArchiveCheckbox(), expectedResults);
        verifyTicks(false, true);
    }

    private void verifyParametricFields(FindParametricCheckbox checked, int expectedResults){
        Waits.loadOrFadeWait();
        int resultsTotal = results.getResultTitles().size();
        int checkboxResults = checked.getResultsCount();

        verifyThat(resultsTotal, is(Math.min(expectedResults, 30)));
        verifyThat(checkboxResults, is(expectedResults));
    }

    private void verifyTicks(boolean plainChecked, boolean simpsonsChecked){
        verifyThat(plainTextCheckbox().isChecked(), is(plainChecked));
        verifyThat(simpsonsArchiveCheckbox().isChecked(), is(simpsonsChecked));
    }

    private FindParametricCheckbox simpsonsArchiveCheckbox(){
        return results.parametricTypeCheckbox("Source Connector", "SIMPSONSARCHIVE");
    }

    private FindParametricCheckbox plainTextCheckbox(){
        return results.parametricTypeCheckbox("Content Type", "TEXT/PLAIN");
    }

    @Test
    public void testUnselectingContentTypeQuicklyDoesNotLeadToError() {
        findPage.search("wolf");
        results.parametricTypeCheckbox("Content Type", "TEXT/HTML").check();
        Waits.loadOrFadeWait();
        results.parametricTypeCheckbox("Content Type", "TEXT/HTML").uncheck();
        results.waitForSearchLoadIndicatorToDisappear(FindResultsPage.Container.MIDDLE);
        assertThat(results.getText().toLowerCase(), not(containsString("error")));
    }

    @Test
    public void testSearch(){
        findPage.search("Red");
        results.waitForSearchLoadIndicatorToDisappear(FindResultsPage.Container.MIDDLE);
        assertThat(results.getText().toLowerCase(), not(containsString("error")));
    }

    @Test
    public void testSortByRelevance() {
        searchWindow.activate();
        getElementFactory().getTopNavBar().search("stars bbc");
        SearchPage searchPage = getElementFactory().getSearchPage();
        searchPage.sortBy(SearchBase.Sort.RELEVANCE);
        List<String> searchTitles = searchPage.getSearchResultTitles(30);

        findWindow.activate();
        findPage.search("stars bbc");

        results.waitForSearchLoadIndicatorToDisappear(FindResultsPage.Container.MIDDLE);

        assertThat(results.getResultTitles(), is(searchTitles));
    }

    @Test
    public void testSortByDate(){
        searchWindow.activate();
        getElementFactory().getTopNavBar().search("stars bbc");
        SearchPage searchPage = getElementFactory().getSearchPage();
        searchPage.sortBy(SearchBase.Sort.DATE);
        List<String> searchTitles = searchPage.getSearchResultTitles(30);

        findWindow.activate();
        findPage.search("stars bbc");

        results.waitForSearchLoadIndicatorToDisappear(FindResultsPage.Container.MIDDLE);
        findPage.sortBy(SearchBase.Sort.DATE);

        assertThat(results.getResultTitles(), is(searchTitles));
    }

    //TODO ALL RELATED CONCEPTS TESTS - probably better to check if text is not("Loading...") rather than not("")
    @Test
    public void testRelatedConceptsHasResults(){
        findPage.search("Danye West");
        for (WebElement concept : results.relatedConcepts()) {
            assertThat(concept, hasTextThat(not(isEmptyOrNullString())));
        }
    }

    @Test
    public void testRelatedConceptsNavigateOnClick(){
        String search = "Red";
        findPage.search(search);
        WebElement topRelatedConcept = results.relatedConcepts().get(0);
        String concept = topRelatedConcept.getText();

        topRelatedConcept.click();
        assertThat(findPage.getAlsoSearchingForTerms(), hasItem(concept));
        assertThat(findPage.getSearchBoxTerm(), is(search));
    }

    @Test
    @KnownBug({"CCUK-3498", "CSA-2066"})
    public void testRelatedConceptsHover(){
        findPage.search("Find");
        WebElement popover = results.hoverOverRelatedConcept(0);
        verifyThat(popover, hasTextThat(not(isEmptyOrNullString())));
        verifyThat(popover.getText(), not(containsString("QueryText-Placeholder")));
        verifyThat(popover.getText(), not(containsString(Errors.Search.RELATED_CONCEPTS)));
        results.unhover();
    }

    @Test
    public void testPinToPosition(){
        String search = "red";
        String trigger = "mate";
        PinToPositionPromotion promotion = new PinToPositionPromotion(1, trigger);

        searchWindow.activate();

        promotionService.deleteAll();

        try {
            String documentTitle = promotionService.setUpPromotion(promotion, search, 1).get(0);

            findWindow.activate();
            findPage.search(trigger);
            assertThat(results.searchResult(1).getTitleString(), is(documentTitle));
        } finally {
            searchWindow.activate();
            promotionService.deleteAll();
        }
    }

    @Test
    public void testPinToPositionThree(){
        String search = "red";
        String trigger = "mate";
        PinToPositionPromotion promotion = new PinToPositionPromotion(3, trigger);

        searchWindow.activate();
        promotionService.deleteAll();

        try {
            String documentTitle = promotionService.setUpPromotion(promotion, search, 1).get(0);

            findWindow.activate();
            findPage.search(trigger);
            assertThat(results.searchResult(3).getTitleString(), is(documentTitle));
        } finally {
            searchWindow.activate();
            promotionService.deleteAll();
        }
    }

    @Test
    @KnownBug("CSA-2098")
    public void testSpotlightPromotions(){
        String search = "Proper";
        String trigger = "Prim";
        SpotlightPromotion spotlight = new SpotlightPromotion(trigger);

        searchWindow.activate();
        promotionService.deleteAll();

        try {
            List<String> createdPromotions = promotionService.setUpPromotion(spotlight, search, 3);

            findWindow.activate();
            findPage.search(trigger);

            List<String> findPromotions = results.getPromotionsTitles();

            assertThat(findPromotions, not(empty()));
            assertThat(createdPromotions, everyItem(isIn(findPromotions)));

            promotionShownCorrectly(results.promotions());
        } finally {
            searchWindow.activate();
            promotionService.deleteAll();
        }
    }

    @Test
    @KnownBug("CSA-2098")
    public void testStaticPromotions(){
        String title = "TITLE";
        String content = "CONTENT";
        String trigger = "LOVE";
        StaticPromotion promotion = new StaticPromotion(title, content, trigger);

        searchWindow.activate();
        promotionService.deleteAll();

        try {
            ((HSOPromotionService) promotionService).setUpStaticPromotion(promotion);

            findWindow.activate();
            findPage.search(trigger);
            List<FindSearchResult> promotions = results.promotions();

            assertThat(promotions.size(), is(1));
            FindSearchResult staticPromotion = promotions.get(0);
            assertThat(staticPromotion.getTitleString(), is(title));
            assertThat(staticPromotion.getDescription(), containsString(content));
            promotionShownCorrectly(staticPromotion);
        } finally {
            searchWindow.activate();
            promotionService.deleteAll();
        }
    }

    @Test
    @KnownBug({"CSA-2058 - titles on Search Optimizer are blank this ruins the test trying to check Find against them","CSA-2067 - 'Rugby', for some reason, is hated by Find"})
    public void testDynamicPromotions(){
        int resultsToPromote = 13;
        String search = "kittens";
        String trigger = "Rugby";
        DynamicPromotion dynamicPromotion = new DynamicPromotion(resultsToPromote, trigger);

        searchWindow.activate();
        promotionService.deleteAll();

        try{
            List<String> promotedDocumentTitles = promotionService.setUpPromotion(dynamicPromotion, search, resultsToPromote);

            findWindow.activate();
            findPage.search(trigger);

            verifyThat(promotedDocumentTitles, everyItem(isIn(results.getPromotionsTitles())));

            promotionShownCorrectly(results.promotions());
        } finally {
            searchWindow.activate();
            promotionService.deleteAll();
        }
    }

    private void promotionShownCorrectly (FindSearchResult promotion){
        verifyThat(promotion.isPromoted(), is(true));
        verifyThat(promotion.star(), displayed());
    }

    private void promotionShownCorrectly (List<FindSearchResult> promotions){
        for(FindSearchResult promotion : promotions){
            promotionShownCorrectly(promotion);
        }
    }

    @Test
    @KnownBug("CSA-1767 - footer not hidden properly")
    @RelatedTo({"CSA-946", "CSA-1656", "CSA-1657", "CSA-1908"})
    public void testMetadata(){
        findPage.search("stars");
        findPage.filterBy(new IndexFilter(Index.DEFAULT));

        for(FindSearchResult searchResult : results.getResults(5)){
            String url = searchResult.getReference();

            try {
                DocumentViewer docViewer = searchResult.openDocumentPreview();
                verifyThat(docViewer.getIndex(), is(Index.DEFAULT));
                verifyThat(docViewer.getReference(), is(url));
                docViewer.close();
            } catch (WebDriverException e) {
                fail("Could not click on title - most likely CSA-1767");
            }
        }
    }

    @Test
    @KnownBug("CCUK-3641")
    public void testAuthor(){
        String author = "FIFA.COM";

        findPage.search("football");
        findPage.filterBy(new IndexFilter("Fifa"));
        findPage.filterBy(new ParametricFilter("Author", author));

        results.waitForSearchLoadIndicatorToDisappear(FindResultsPage.Container.MIDDLE);

        assertThat(results.resultsDiv(), not(containsText("An error occurred")));

        List<FindSearchResult> searchResults = results.getResults();

        for(int i = 0; i < 6; i++){
            DocumentViewer documentViewer = searchResults.get(i).openDocumentPreview();
            verifyThat(documentViewer.getAuthor(), equalToIgnoringCase(author));
            documentViewer.close();
        }
    }

    @Test
    public void testFilterByIndex(){
        findPage.search("Sam");

        SearchResult searchResult = results.searchResult(1);
        String titleString = searchResult.getTitleString();

        DocumentViewer docViewer = searchResult.openDocumentPreview();
        Index index = docViewer.getIndex();

        docViewer.close();

        findPage.filterBy(new IndexFilter(index));

        assertThat(results.searchResult(1).getTitleString(), is(titleString));
    }

    @Test
    public void testFilterByIndexOnlyContainsFilesFromThatIndex(){
        findPage.search("Happy");

        // TODO: what if this index has no results?
        //This breaks if using default index
        String indexTitle = findPage.getPrivateIndexNames().get(1);
        findPage.filterBy(new IndexFilter(indexTitle));
        DocumentViewer docViewer = results.searchResult(1).openDocumentPreview();
        for(int i = 0; i < 5; i++){
            assertThat(docViewer.getIndex().getDisplayName(), is(indexTitle));
            docViewer.next();
        }
    }

    @Test
    public void testQuicklyDoubleClickingIndexDoesNotLeadToError(){
        findPage.search("index");
        // async filters
        new IndexFilter(Index.DEFAULT).apply(findPage);
        IndexFilter.PRIVATE.apply(findPage);
        results.waitForSearchLoadIndicatorToDisappear(FindResultsPage.Container.MIDDLE);
        assertThat(results.resultsDiv().getText().toLowerCase(), not(containsString("an error occurred")));
    }

    @Test
    public void testPreDefinedWeekHasSameResultsAsCustomWeek(){
        preDefinedDateFiltersVersusCustomDateFilters(FindResultsPage.DateEnum.WEEK);
    }

    @Test
    public void testPreDefinedMonthHasSameResultsAsCustomMonth(){
        preDefinedDateFiltersVersusCustomDateFilters(FindResultsPage.DateEnum.MONTH);
    }

    @Test
    public void testPreDefinedYearHasSameResultsAsCustomYear(){
        preDefinedDateFiltersVersusCustomDateFilters(FindResultsPage.DateEnum.YEAR);
    }

    private void preDefinedDateFiltersVersusCustomDateFilters(FindResultsPage.DateEnum period){
        findPage.search("Rugby");

        results.toggleDateSelection(period);
        List<String> preDefinedResults = results.getResultTitles();
        findPage.filterBy(new StringDateFilter().from(getDate(period)));
        List<String> customResults = results.getResultTitles();

        assertThat(preDefinedResults, is(customResults));
    }

    private Date getDate(FindResultsPage.DateEnum period) {
        Calendar cal = Calendar.getInstance();

        if (period != null) {
            switch (period) {
                case WEEK:
                    cal.add(Calendar.DATE,-7);
                    break;
                case MONTH:
                    cal.set(Calendar.MONTH, cal.get(Calendar.MONTH) - 1);
                    break;
                case YEAR:
                    cal.set(Calendar.YEAR, cal.get(Calendar.YEAR) - 1);
                    break;
            }
        }
        return cal.getTime();
    }

    @Ignore //TODO seems to have broken
    @Test
    public void testAllParametricFieldsAreShown() throws HodErrorException {
        final HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
        httpClientBuilder.setProxy(new HttpHost("proxy.sdc.hp.com", 8080));


        final HodServiceConfig config = new HodServiceConfig.Builder("https://api.int.havenondemand.com")
                .setHttpClient(httpClientBuilder.build()) // use a custom Apache HttpClient - useful if you're behind a proxy
                .build();

        final AuthenticationService authenticationService = new AuthenticationServiceImpl(config);
        final RetrieveIndexFieldsService retrieveIndexFieldsService = new RetrieveIndexFieldsServiceImpl(config);

        final TokenProxy tokenProxy = authenticationService.authenticateApplication(
                new ApiKey("098b8420-f85f-4164-b8a8-42263e9405a1"),
                "733d64e8-41f7-4c46-a1c8-60d083255159",
                getCurrentUser().getDomain(),
                TokenType.simple
        );

        Set<String> parametricFields = new HashSet<>();

        findPage.search("Something");

        for (String indexName : findPage.getPrivateIndexNames()) {
            RetrieveIndexFieldsResponse retrieveIndexFieldsResponse = retrieveIndexFieldsService.retrieveIndexFields(tokenProxy,
                    new ResourceIdentifier(getCurrentUser().getDomain(), indexName), new RetrieveIndexFieldsRequestBuilder().setFieldType(FieldType.parametric));

            parametricFields.addAll(retrieveIndexFieldsResponse.getAllFields());
        }

        for(String field : parametricFields) {
            try {
                assertTrue(results.parametricContainer(field).isDisplayed());
            } catch (ElementNotVisibleException | NotFoundException e) {
                fail("Could not find field '"+field+"'");
            }
        }
    }

    @Test
    @KnownBug("CSA-1767 - footer not hidden properly")
    public void testViewDocumentsOpenFromFind(){
        findPage.search("Review");

        for(FindSearchResult result : results.getResults(5)){
            try {
                DocumentViewer docViewer = result.openDocumentPreview();
                verifyDocumentViewer(docViewer);
                docViewer.close();
            } catch (WebDriverException e){
                fail("Could not click on title - most likely CSA-1767");
            }
        }
    }

    @Test
    public void testViewDocumentsOpenWithArrows(){
        findPage.search("Review");

        results.waitForSearchLoadIndicatorToDisappear(FindResultsPage.Container.MIDDLE);
        DocumentViewer docViewer = results.searchResult(1).openDocumentPreview();
        for(int i = 0; i < 5; i++) {
            verifyDocumentViewer(docViewer);
            docViewer.next();
        }
    }

    private void verifyDocumentViewer(DocumentViewer docViewer) {
        verifyThat("document visible", docViewer, displayed());
        verifyThat("next button visible", docViewer.nextButton(), displayed());
        verifyThat("previous button visible", docViewer.prevButton(), displayed());

        String handle = getDriver().getWindowHandle();
        getDriver().switchTo().frame(docViewer.frame());

        //TODO these aren't working properly - did Fred not fix these?
        verifyThat("no backend error", getDriver().findElements(new Locator().withTagName("h1").containingText("500")), empty());
        verifyThat("no view server error", getDriver().findElements(new Locator().withTagName("h2").containingCaseInsensitive("error")), empty());
        getDriver().switchTo().window(handle);
    }

    @Test
    public void testDateRemainsWhenClosingAndReopeningDateFilters(){
        findPage.search("Corbyn");

        Date start = getDate(FindResultsPage.DateEnum.MONTH);
        Date end = getDate(FindResultsPage.DateEnum.WEEK);

        findPage.filterBy(new StringDateFilter().from(start).until(end));
        Waits.loadOrFadeWait();
        for (int unused = 0; unused < 3; unused++) {
            results.toggleDateSelection(FindResultsPage.DateEnum.CUSTOM);
            Waits.loadOrFadeWait();
        }

        assertThat(findPage.fromDateInput().getValue(), is(findPage.formatInputDate(start)));
        assertThat(findPage.untilDateInput().getValue(), is(findPage.formatInputDate(end)));
    }

    @Test
    public void testFileTypes(){
        findPage.search("love ");

        for(FileType f : FileType.values()) {
            findPage.filterBy(new ParametricFilter("Content Type",f.getSidebarString()));

            for(FindSearchResult result : results.getResults()){
                assertThat(result.getIcon().getAttribute("class"), containsString(f.getFileIconString()));
            }

            findPage.filterBy(new ParametricFilter("Content Type",f.getSidebarString()));
        }
    }

    @Test
    public void testSynonyms() throws InterruptedException {
        String nonsense = "iuhdsafsaubfdja";
        searchWindow.activate();
        keywordService.deleteAll(KeywordFilter.ALL);

        Waits.loadOrFadeWait();

        findWindow.activate();
        findPage.search(nonsense);

        results.waitForSearchLoadIndicatorToDisappear(FindResultsPage.Container.MIDDLE);
        assertThat(results.getText(), noDocs);

        findPage.search("Cat");
        results.waitForSearchLoadIndicatorToDisappear(FindResultsPage.Container.MIDDLE);
        assertThat(results.getText(), not(noDocs));

        searchWindow.activate();
        keywordService.addSynonymGroup(Language.ENGLISH, "cat", nonsense);

        /* need a separate session due to caching */
        Session secondSession = getSessionRegistry().startSession(config.getFindUrl());
        try {
            FindPage otherFindPage = initialiseSession(secondSession);
            otherFindPage.search("Cat");
            FindResultsPage otherResults = otherFindPage.getResultsPage();
            String firstTitle = otherResults.searchResult(1).getTitleString();

            otherFindPage.search(nonsense);
            assertThat(otherResults.getText(), not(noDocs));
            verifyThat(otherResults.searchResult(1).getTitleString(), is(firstTitle));

        } finally {
            getSessionRegistry().endSession(secondSession);
        }
    }

    @Test
    public void testBlacklist() throws InterruptedException {
        searchWindow.activate();
        keywordService.deleteAll(KeywordFilter.ALL);

        Waits.loadOrFadeWait();

        findWindow.activate();
        findPage.search("Cat");

        assertThat(results.getText(), not(noDocs));

        findPage.search("Holder");

        searchWindow.activate();

        keywordService.addBlacklistTerms(Language.ENGLISH, "cat");

        /* need a separate session due to caching */
        Session secondSession = getSessionRegistry().startSession(config.getFindUrl());
        try {
            FindPage otherFindPage = initialiseSession(secondSession);
            otherFindPage.search("Cat");

            assertThat(otherFindPage.getResultsPage(), hasTextThat(noDocs));
        } finally {
            getSessionRegistry().endSession(secondSession);
        }
    }

    // TODO: this does not belong here
    private FindPage initialiseSession(Session session) {
        HSODFindElementFactory otherElementFactory = new HSODFind(session.getActiveWindow()).elementFactory();
        loginTo(otherElementFactory.getLoginPage(), session.getDriver(), config.getDefaultUser());
        return otherElementFactory.getFindPage();
    }

    @Test   @Ignore("Not implemented")
    public void testOverlappingSynonyms(){}

    @Test
    public void testBooleanOperators(){
        String termOne = "musketeers";
        String termTwo = "\"dearly departed\"";

        findPage.search(termOne);
        List<String> musketeersSearchResults = results.getResultTitles();
        int numberOfMusketeersResults = musketeersSearchResults.size();

        findPage.search(termTwo);
        List<String> dearlyDepartedSearchResults = results.getResultTitles();
        int numberOfDearlyDepartedResults = dearlyDepartedSearchResults.size();

        findPage.search(termOne + " AND " + termTwo);
        List<String> andResults = results.getResultTitles();
        int numberOfAndResults = andResults.size();

        assertThat(numberOfMusketeersResults,greaterThanOrEqualTo(numberOfAndResults));
        assertThat(numberOfDearlyDepartedResults, greaterThanOrEqualTo(numberOfAndResults));
        String[] andResultsArray = andResults.toArray(new String[andResults.size()]);
        assertThat(musketeersSearchResults, hasItems(andResultsArray));
        assertThat(dearlyDepartedSearchResults, hasItems(andResultsArray));

        findPage.search(termOne + " OR " + termTwo);
        List<String> orResults = results.getResultTitles();
        Set<String> concatenatedResults = new HashSet<>(ListUtils.union(musketeersSearchResults, dearlyDepartedSearchResults));
        assertThat(orResults.size(), is(concatenatedResults.size()));
        assertThat(orResults, containsInAnyOrder(concatenatedResults.toArray()));

        findPage.search(termOne + " XOR " + termTwo);
        List<String> xorResults = results.getResultTitles();
        concatenatedResults.removeAll(andResults);
        assertThat(xorResults.size(), is(concatenatedResults.size()));
        assertThat(xorResults, containsInAnyOrder(concatenatedResults.toArray()));

        findPage.search(termOne + " NOT " + termTwo);
        List<String> notTermTwo = results.getResultTitles();
        Set<String> t1NotT2 = new HashSet<>(concatenatedResults);
        t1NotT2.removeAll(dearlyDepartedSearchResults);
        assertThat(notTermTwo.size(), is(t1NotT2.size()));
        assertThat(notTermTwo, containsInAnyOrder(t1NotT2.toArray()));

        findPage.search(termTwo + " NOT " + termOne);
        List<String> notTermOne = results.getResultTitles();
        Set<String> t2NotT1 = new HashSet<>(concatenatedResults);
        t2NotT1.removeAll(musketeersSearchResults);
        assertThat(notTermOne.size(), is(t2NotT1.size()));
        assertThat(notTermOne, containsInAnyOrder(t2NotT1.toArray()));
    }

    //DUPLICATE SEARCH TEST (almost)
    @Test
    public void testCorrectErrorMessageDisplayed() {
        //TODO: map error messages to application type

        List<String> boolOperators = Arrays.asList("OR", "WHEN", "SENTENCE", "DNEAR");
        List<String> stopWords = Arrays.asList("a", "the", "of", "SOUNDEX"); //According to IDOL team SOUNDEX isn't considered a boolean operator without brackets

        for (final String searchTerm : boolOperators) {
            findPage.search(searchTerm);
            verifyThat("Correct error message for searchterm: " + searchTerm, findPage.getText(), containsString(Errors.Search.OPERATORS));
        }

        for (final String searchTerm : stopWords) {
            findPage.search(searchTerm);
            verifyThat("Correct error message for searchterm: " + searchTerm, findPage.getText(), containsString(Errors.Search.STOPWORDS));
        }
    }

    //DUPLICATE SEARCH TEST
    @Test
    public void testAllowSearchOfStringsThatContainBooleansWithinThem() {
        final List<String> hiddenBooleansProximities = Arrays.asList("NOTed", "ANDREW", "ORder", "WHENCE", "SENTENCED", "PARAGRAPHING", "NEARLY", "SENTENCE1D", "PARAGRAPHING", "PARAGRAPH2inG", "SOUNDEXCLUSIVE", "XORING", "EORE", "DNEARLY", "WNEARING", "YNEARD", "AFTERWARDS", "BEFOREHAND", "NOTWHENERED");
        for (final String hiddenBooleansProximity : hiddenBooleansProximities) {
            findPage.search(hiddenBooleansProximity);
            Waits.loadOrFadeWait();
            verifyThat(hiddenBooleansProximity + " searched for successfully", findPage.getText(), not(containsString("An error has occurred")));
        }
    }

    //DUPLICATE
    @Test
    public void testSearchParentheses() {
        List<String> testSearchTerms = Arrays.asList("(",")",") (",")war"); //"()" appears to be fine

        for(String searchTerm : testSearchTerms){
            findPage.search(searchTerm);

            assertThat(results, containsText(Errors.Search.OPERATORS));
        }
    }

    //DUPLICATE
    @Test
    @KnownBug({"IOD-8454","CCUK-3634"})
    public void testSearchQuotationMarks() {
        List<String> testSearchTerms = Arrays.asList("\"", "", "\"word", "\" word", "\" wo\"rd\""); //"\"\"" seems okay and " "
        for (String searchTerm : testSearchTerms){
            findPage.search(searchTerm);
            Waits.loadOrFadeWait();
            assertThat(results, containsText(Errors.Search.QUOTES));
        }
    }

    //DUPLICATE
    @Test
    public void testWhitespaceSearch() {
        try {
            findPage.search(" ");
        } catch (TimeoutException e) { /* Expected behaviour */ }

        assertThat(findPage.footerLogo(), displayed());

        findPage.search("Kevin Costner");
        List<String> resultTitles = results.getResultTitles();

        findPage.search(" ");

        assertThat(results.getResultTitles(), is(resultTitles));
        assertThat(findPage.parametricContainer().getText(), not(isEmptyOrNullString()));
    }

    @Test
    @KnownBug("CSA-1577")
    public void testClickingCustomDateFilterDoesNotRefreshResults(){
        findPage.search("O Captain! My Captain!");
        // may not happen the first time
        for (int unused = 0; unused < 5; unused++) {
            results.toggleDateSelection(FindResultsPage.DateEnum.CUSTOM);
            assertThat(results.resultsDiv().getText(), not(containsString("Loading")));
        }
    }

    @Test
    @KnownBug("CSA-1665")
    public void testSearchTermInResults(){
        String searchTerm = "Tiger";

        findPage.search(searchTerm);

        for(WebElement searchElement : getDriver().findElements(By.xpath("//*[not(self::h4) and contains(text(),'" + searchTerm + "')]"))){
            if(searchElement.isDisplayed()) {        //They can become hidden if they're too far in the summary
                verifyThat(searchElement.getText(), containsString(searchTerm));
            }
            verifyThat(searchElement, not(hasTagName("a")));
            verifyThat(searchElement, hasClass("search-text"));
        }
    }

    // TODO: testMultiWordSearchTermInResults
    @Test
    public void testRelatedConceptsInResults(){
        findPage.search("Tiger");

        for(WebElement relatedConceptLink : results.relatedConcepts()){
            String relatedConcept = relatedConceptLink.getText();
            for (WebElement relatedConceptElement : getDriver().findElements(By.xpath("//*[contains(@class,'middle-container')]//*[not(self::h4) and contains(text(),'" + relatedConcept + "')]"))) {
                if (relatedConceptElement.isDisplayed()) {        //They can become hidden if they're too far in the summary
                    verifyThat(relatedConceptElement, containsTextIgnoringCase(relatedConcept));
                }
                verifyThat(relatedConceptElement, hasTagName("a"));
                verifyThat(relatedConceptElement, hasClass("clickable"));
            }
        }
    }

    @Test
    public void testSimilarDocumentsShowUp() throws InterruptedException {
        findPage.search("Doe");

        for (int i = 1; i <= 5; i++) {
            FindSearchResult searchResult = results.getResult(i);
            String title = searchResult.getTitleString();
            searchResult.similarDocuments().click();

            results.waitForSearchLoadIndicatorToDisappear(FindResultsPage.Container.MIDDLE);

            WebElement similarDocumentsPage = getDriver().findElement(By.className("suggest-service-view-container"));
            WebElement similarDocsPageTitle = similarDocumentsPage.findElement(By.cssSelector(".m-b-nil.bold"));
            WebElement similarDocsResult = null;
            WebElement backButton = similarDocumentsPage.findElement(By.className("service-view-back-button"));

            try {
                similarDocsResult = similarDocumentsPage.findElement(By.className("main-results-container"));
            } catch (NoSuchElementException e) {
                LoggerFactory.getLogger(FindITCase.class).error("No results for similar documents");
            }

            verifyThat(getDriver().getCurrentUrl(), containsString("suggest"));
            verifyThat(similarDocsPageTitle, containsTextIgnoringCase("Similar results to document with title \"" + title + "\""));
            verifyThat(similarDocsResult, notNullValue());

            backButton.click();
        }
    }

    @Test
    @KnownBug("CSA-1630")
    public void testAllPromotedDocumentsHaveTitles(){
        searchWindow.activate();

        PromotionService promotionService = getApplication().promotionService();

        try {
            promotionService.setUpPromotion(new SpotlightPromotion(Promotion.SpotlightType.HOTWIRE, "Tiger"), "scg-2", 10);

            findWindow.activate();

            findPage.search("Tiger");

            for(String title : results.getPromotionsTitles()){
                assertThat(title, is(not("")));
            }

        } finally {
            searchWindow.activate();
            promotionService.deleteAll();
        }
    }

    @Test
    @KnownBug({"CSA-1726", "CSA-1763"})
    public void testPublicIndexesVisibleNotSelectedByDefault(){
        findPage.search("Marina and the Diamonds");

        verifyThat("public indexes are visible", findPage.indexesTree().publicIndexes(), not(emptyIterable()));
        verifyThat(findPage.getSelectedPublicIndexes(), empty());
    }

    @Test
    @KnownBug("CSA-2082")
    public void testAutoScroll(){
        findPage.search("my very easy method just speeds up naming ");

        verifyThat(results.getResults().size(), lessThanOrEqualTo(30));

        scrollToBottom();
        verifyThat(results.getResults().size(), allOf(greaterThanOrEqualTo(30), lessThanOrEqualTo(60)));

        scrollToBottom();
        verifyThat(results.getResults().size(), allOf(greaterThanOrEqualTo(60), lessThanOrEqualTo(90)));

        List<String> titles = results.getResultTitles();
        Set<String> titlesSet = new HashSet<>(titles);

        verifyThat("No duplicate titles", titles.size(), is(titlesSet.size()));
    }

    @Test
    public void testViewportSearchResultNumbers(){
        findPage.search("Messi");

        results.getResult(1).openDocumentPreview();
        verifyDocViewerTotalDocuments(30);

        scrollToBottom();
        results.getResult(31).openDocumentPreview();
        verifyDocViewerTotalDocuments(60);

        scrollToBottom();
        results.getResult(61).openDocumentPreview();
        verifyDocViewerTotalDocuments(90);
    }

    @Test
    @KnownBug("CCUK-3647")
    public void testLessThan30ResultsDoesntAttemptToLoadMore() {
        findPage.search("roland garros");
        findPage.filterBy(new IndexFilter("fifa"));

        results.getResult(1).openDocumentPreview();
        verifyDocViewerTotalDocuments(lessThanOrEqualTo(30));

        scrollToBottom();
        verifyThat(results.resultsDiv(), not(containsText("results found")));
    }

    @Test
    public void testBetween30And60Results(){
        findPage.search("idol");
        findPage.filterBy(new IndexFilter("sitesearch"));

        scrollToBottom();
        results.getResult(1).openDocumentPreview();
        verifyDocViewerTotalDocuments(lessThanOrEqualTo(60));

        Waits.loadOrFadeWait();

        verifyThat(results.resultsDiv(), containsText("No more results found"));
    }

    @Test
    public void testNoResults(){
        findPage.search("thissearchwillalmostcertainlyreturnnoresults");

        verifyThat(results.resultsDiv(), containsText("No results found"));

        scrollToBottom();

        int occurrences = StringUtils.countMatches(results.resultsDiv().getText(), "results found");
        verifyThat("Only one message showing at the bottom of search results", occurrences, is(1));
    }

    private void scrollToBottom() {
        for(int i = 0; i < 10; i++){
            new Actions(getDriver()).sendKeys(Keys.PAGE_DOWN).perform();
        }
        results.waitForSearchLoadIndicatorToDisappear(FindResultsPage.Container.MIDDLE);
    }

    private void verifyDocViewerTotalDocuments(int docs){
        verifyDocViewerTotalDocuments(is(docs));
    }

    private void verifyDocViewerTotalDocuments(Matcher matcher){
        DocumentViewer docViewer = DocumentViewer.make(getDriver());
        verifyThat(docViewer.getTotalDocumentsNumber(), matcher);
        docViewer.close();
    }

    @Test
    @KnownBug("CCUK-3624")
    public void testRefreshEmptyQuery() throws InterruptedException {
        findPage.search("something");
        findPage.search("");
        Thread.sleep(5000);

        getDriver().navigate().refresh();
        findPage = findApp.elementFactory().getFindPage();

        verifyThat(findPage.getSearchBoxTerm(), is(""));
        verifyThat("taken back to landing page after refresh", findPage.footerLogo(), displayed());
    }

    private enum FileType {
        HTML("TEXT/HTML","html"),
        PDF("APPLICATION/PDF","pdf"),
        PLAIN("TEXT/PLAIN","file");

        private final String sidebarString;
        private final String fileIconString;

        FileType(String sidebarString, String fileIconString){
            this.sidebarString = sidebarString;
            this.fileIconString = fileIconString;
        }

        public String getFileIconString() {
            return fileIconString;
        }

        public String getSidebarString() {
            return sidebarString;
        }
    }

}
