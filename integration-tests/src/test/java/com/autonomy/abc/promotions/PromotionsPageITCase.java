package com.autonomy.abc.promotions;

import com.autonomy.abc.config.ABCTestBase;
import com.autonomy.abc.config.TestConfig;
import com.autonomy.abc.selenium.actions.PromotionActionFactory;
import com.autonomy.abc.selenium.config.ApplicationType;
import com.autonomy.abc.selenium.element.Dropdown;
import com.autonomy.abc.selenium.element.Editable;
import com.autonomy.abc.selenium.element.FormInput;
import com.autonomy.abc.selenium.menu.NavBarTabId;
import com.autonomy.abc.selenium.page.promotions.PromotionsDetailPage;
import com.autonomy.abc.selenium.page.promotions.PromotionsPage;
import com.autonomy.abc.selenium.page.search.SearchPage;
import com.autonomy.abc.selenium.promotions.*;
import com.autonomy.abc.selenium.search.LanguageFilter;
import com.autonomy.abc.selenium.search.Search;
import com.autonomy.abc.selenium.search.SearchActionFactory;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.openqa.selenium.*;

import java.net.MalformedURLException;
import java.util.List;

import static com.autonomy.abc.framework.ABCAssert.assertThat;
import static com.autonomy.abc.framework.ABCAssert.verifyThat;
import static com.autonomy.abc.matchers.ElementMatchers.containsElement;
import static com.autonomy.abc.matchers.ElementMatchers.containsText;
import static com.autonomy.abc.matchers.PromotionsMatchers.promotionsList;
import static com.autonomy.abc.matchers.PromotionsMatchers.triggerList;
import static org.hamcrest.Matchers.*;
import static org.junit.Assume.assumeThat;

public class PromotionsPageITCase extends ABCTestBase {

	public PromotionsPageITCase(final TestConfig config, final String browser, final ApplicationType appType, final Platform platform) {
		super(config, browser, appType, platform);
	}

	private PromotionsPage promotionsPage;
	private PromotionsDetailPage promotionsDetailPage;
	private SearchActionFactory searchActionFactory;
	private PromotionActionFactory promotionActionFactory;

	@Before
	public void setUp() throws MalformedURLException {
		body.getSideNavBar().switchPage(NavBarTabId.PROMOTIONS);
		promotionsPage = getElementFactory().getPromotionsPage();
		// TODO: occasional stale element?
		searchActionFactory = new SearchActionFactory(getApplication(), getElementFactory());
		promotionActionFactory = new PromotionActionFactory(getApplication(), getElementFactory());
		promotionActionFactory.makeDeleteAll().apply();
	}

	// TODO: use promotionActionFactory, but need to be able to access
	private List<String> setUpPromotion(Search search, int numberOfDocs, Promotion promotion) {
		search.apply();
		SearchPage searchPage = getElementFactory().getSearchPage();
		searchPage.promoteTheseDocumentsButton().click();
		List<String> promotedDocTitles = searchPage.addToBucket(numberOfDocs);
		if (promotion instanceof DynamicPromotion) {
			searchPage.promoteThisQueryButton().click();
		} else {
			searchPage.waitUntilClickableThenClick(searchPage.promoteTheseItemsButton());
		}
		promotion.makeWizard(getElementFactory().getCreateNewPromotionsPage()).apply();
		// wait for search page to load before navigating away
		getElementFactory().getSearchPage();
		promotionsDetailPage = promotion.getDetailsPage(body, getElementFactory());
		return promotedDocTitles;
	}

	private List<String> setUpPromotion(Search search, Promotion promotion) {
		return setUpPromotion(search, 1, promotion);
	}

	private List<String> setUpCarsPromotion(int numberOfDocs) {
//		final List<String> promotedDocTitles = promotionsPage.setUpANewMultiDocPromotion("English", "cars", "Sponsored", "wheels", 2, getConfig().getType().getName());
		return setUpPromotion(searchActionFactory.makeSearch("cars"), numberOfDocs, new SpotlightPromotion("wheels"));
	}

	private Search search(String searchTerm, String language) {
		return searchActionFactory.makeSearch(searchTerm).applyFilter(new LanguageFilter(language));
	}

	private void goToDetails(String text) {
		promotionsPage.getPromotionLinkWithTitleContaining(text).click();
		promotionsDetailPage = getElementFactory().getPromotionsDetailPage();
	}

	@Test
	public void testNewPromotionButtonLink() {
		promotionsPage.promoteExistingButton().click();
		verifyThat("correct URL", getDriver().getCurrentUrl().endsWith("promotions/new"));
		verifyThat("correct title", getApplication().createAppBody(getDriver()).getTopNavBar(), containsText("Create New Promotion"));
	}

	// TODO: should work after CCUK-3394
	@Test
	public void testCorrectDocumentsInPromotion() {
		List<String> promotedDocTitles = setUpCarsPromotion(2);
		List<String> promotedList = promotionsDetailPage.getPromotedTitles();
		verifyThat(promotedDocTitles, everyItem(isIn(promotedList)));
	}

	@Test
	public void testDeletePromotedDocuments() {
		List<String> promotedDocTitles = setUpCarsPromotion(4);
		int numberOfDocuments = promotionsDetailPage.getPromotedTitles().size();
		verifyThat(numberOfDocuments, is(4));

		for (final String title : promotedDocTitles) {
			promotionsDetailPage.removablePromotedDocument(title).removeAndWait();
			numberOfDocuments--;

			if (numberOfDocuments == 1) {
				assertThat(promotionsDetailPage.getPromotedTitles(), hasSize(1));
				verifyThat("remove document button is not visible when a single document", promotionsPage, not(containsElement(By.className("remove-document-reference"))));
				break;
			}
		}
	}

	@Test
	public void testWhitespaceTrigger() {
		setUpCarsPromotion(1);

		FormInput triggerBox = promotionsDetailPage.triggerAddBox();
		try {
			triggerBox.setAndSubmit("");
		} catch (Exception e) {
			e.printStackTrace();
		}

		verifyThat(promotionsPage, triggerList(hasSize(1)));

		promotionsDetailPage.addTrigger("trigger");
		verifyThat("added valid trigger", promotionsPage, triggerList(hasSize(2)));

		String[] invalidTriggers = {"   ", " trigger", "\t"};
		for (String trigger : invalidTriggers) {
			promotionsDetailPage.addTrigger(trigger);
			verifyThat("'" + trigger + "' is not accepted as a valid trigger", promotionsPage, triggerList(hasSize(2)));
		}
	}

	@Test
	public void testQuotesTrigger() throws InterruptedException {
		setUpCarsPromotion(1);

		verifyThat(promotionsPage, triggerList(hasSize(1)));

		promotionsDetailPage.addTrigger("bag");
		verifyThat("added valid trigger", promotionsPage, triggerList(hasSize(2)));

		String[] invalidTriggers = {"\"bag", "bag\"", "\"bag\""};
		for (String trigger : invalidTriggers) {
			promotionsDetailPage.addTrigger(trigger);
			verifyThat("'" + trigger + "' is not accepted as a valid trigger", promotionsPage, triggerList(hasSize(2)));
		}
	}

	@Test
	public void testCommasTrigger() {
		setUpCarsPromotion(1);
		verifyThat(promotionsPage, triggerList(hasSize(1)));

		promotionsDetailPage.addTrigger("France");
		verifyThat(promotionsPage, triggerList(hasSize(2)));

		String[] invalidTriggers = {",Germany", "Ita,ly Spain", "Ireland, Belgium", "UK , Luxembourg"};
		for (String trigger : invalidTriggers) {
			promotionsDetailPage.addTrigger(trigger);
			verifyThat("'" + trigger + "' does not add a new trigger", promotionsPage, triggerList(hasSize(2)));
			verifyThat("'" + trigger + "' produces an error message", promotionsPage, containsText("Terms may not contain commas. Separate words and phrases with whitespace."));
		}

		promotionsDetailPage.addTrigger("Greece Romania");
		assertThat(promotionsPage, triggerList(hasSize(4)));
		assertThat("error message no longer showing", promotionsPage, not(containsText("Terms may not contain commas. Separate words and phrases with whitespace.")));
	}

	@Test
	public void testHTMLTrigger() {
		setUpCarsPromotion(1);
		final String trigger = "<h1>Hi</h1>";
		promotionsDetailPage.triggerAddBox().setAndSubmit(trigger);

		assertThat("triggers are HTML escaped", promotionsPage, triggerList(hasItem(trigger)));
	}

	// fails on-prem due to CCUK-2671
	@Test
	public void testAddRemoveTriggers() throws InterruptedException {
		setUpCarsPromotion(1);

		promotionsDetailPage.addTrigger("alpha");
		promotionsDetailPage.waitForTriggerRefresh();
		promotionsDetailPage.trigger("wheels").removeAndWait();
		verifyThat(promotionsPage, triggerList(hasSize(1)));

		verifyThat(promotionsPage, triggerList(not(hasItem("wheels"))));

		promotionsDetailPage.addTrigger("beta gamma delta");
		promotionsDetailPage.trigger("gamma").removeAsync();
		promotionsDetailPage.trigger("alpha").removeAsync();
		promotionsDetailPage.addTrigger("epsilon");
		promotionsDetailPage.trigger("beta").removeAsync();
		promotionsDetailPage.waitForTriggerRefresh();

		verifyThat(promotionsPage, triggerList(hasSize(2)));
		verifyThat(promotionsPage, triggerList(not(hasItem("beta"))));
		verifyThat(promotionsPage, triggerList(hasItem("epsilon")));

		promotionsDetailPage.trigger("epsilon").removeAndWait();
		verifyThat(promotionsPage, not(containsElement(By.className("remove-word"))));
	}

	@Test
	public void testBackButton() {
		setUpCarsPromotion(1);
		promotionsDetailPage.backButton().click();
		assertThat("Back button redirects to main promotions page", getDriver().getCurrentUrl().endsWith("promotions"));
	}

	@Test
	public void testEditPromotionName() throws InterruptedException {
		setUpCarsPromotion(1);
		Editable title = promotionsDetailPage.promotionTitle();
		verifyThat(title.getValue(), (is("Spotlight for: wheels")));

		String[] newTitles = {"Fuzz", "<script> alert(\"hi\") </script>"};
		for (String newTitle : newTitles) {
			title.setValueAndWait(newTitle);
			verifyThat(title.getValue(), (is(newTitle)));
		}
	}

	@Test
	public void testEditPromotionType() {
		// cannot edit promotion type for hosted
		assumeThat(config.getType(), equalTo(ApplicationType.ON_PREM));
		setUpCarsPromotion(1);
		verifyThat(promotionsDetailPage.getPromotionType(), is("Sponsored"));

		Dropdown dropdown = promotionsDetailPage.spotlightTypeDropdown();
		dropdown.select("Hotwire");
		verifyThat(dropdown.getValue(), is("Hotwire"));

		dropdown.select("Top Promotions");
		verifyThat(dropdown.getValue(), is("Top Promotions"));

		dropdown.select("Sponsored");
		verifyThat(dropdown.getValue(), is("Sponsored"));
	}

	@Test
	public void testDeletePromotions() throws InterruptedException {
		String[] searchTerms = {"rabbit", "horse", "script"};
		String[] triggers = {"bunny", "pony", "<script> document.body.innerHTML = '' </script>"};
		for (int i=0; i<searchTerms.length; i++) {
			setUpPromotion(searchActionFactory.makeSearch(searchTerms[i]), new SpotlightPromotion(triggers[i]));
			promotionsDetailPage.backButton().click();
		}

		// "script" gets mangled
		String[] searchableTriggers = {"bunny", "pony", "script"};
		for (String trigger : searchableTriggers) {
			verifyThat(promotionsPage, promotionsList(hasItem(containsText(trigger))));
		}
		verifyThat(promotionsPage, promotionsList(hasSize(3)));

		promotionActionFactory.makeDelete("bunny").apply();

		verifyThat("promotion 'pony' still exists", promotionsPage, promotionsList(hasItem(containsText("pony"))));
		verifyThat("promotion 'script' still exists", promotionsPage, promotionsList(hasItem(containsText("script"))));
		verifyThat("deleted promotion 'bunny'", promotionsPage, promotionsList(hasSize(2)));

		promotionActionFactory.makeDelete("script").apply();

		verifyThat("promotion 'pony' still exists", promotionsPage, promotionsList(hasItem(containsText("pony"))));
		verifyThat("deleted promotion 'bunny'", promotionsPage, promotionsList(hasSize(1)));

		promotionActionFactory.makeDelete("pony").apply();

		verifyThat("deleted promotion 'pony'", promotionsPage, promotionsList(hasSize(0)));
	}

	@Ignore
	@Test
	public void testAddingLotsOfDocsToAPromotion() {
		setUpPromotion(searchActionFactory.makeSearch("sith"), 100, new SpotlightPromotion("darth sith"));
		assertThat(promotionsPage, promotionsList(hasSize(100)));
	}

	private void renamePromotionContaining(String oldTitle, String newTitle) {
		goToDetails(oldTitle);
		promotionsDetailPage.promotionTitle().setValueAndWait(newTitle);
		promotionsDetailPage.backButton().click();
		promotionsPage = getElementFactory().getPromotionsPage();
	}

	@Test
	public void testPromotionFilter() throws InterruptedException {
//		assumeThat(config.getType(), equalTo(ApplicationType.ON_PREM));

		Search[] searches = {
				search("chien", "French"),
				search("الكلب", "Arabic"),
				search("dog", "English"),
				search("mbwa", "Swahili"),
				search("mbwa", "Swahili"),
				search("hond", "Afrikaans"),
				search("hond", "Afrikaans"),
		};
		Promotion[] promotions = {
				new SpotlightPromotion(Promotion.SpotlightType.HOTWIRE, "woof bark"),
				new SpotlightPromotion(Promotion.SpotlightType.TOP_PROMOTIONS, "dog chien"),
				new SpotlightPromotion(Promotion.SpotlightType.SPONSORED, "hound pooch"),
				new PinToPositionPromotion(3, "woof swahili"),
				new PinToPositionPromotion(3, "pooch swahili"),
				new DynamicPromotion(Promotion.SpotlightType.HOTWIRE, "pooch hond wolf"),
				new DynamicPromotion(5, "lupo wolf")
		};

		for (int i = 0; i < searches.length; i++) {
			setUpPromotion(searches[i], promotions[i]);
			promotionsDetailPage.backButton().click();
		}
		assertThat(promotionsPage, promotionsList(hasSize(searches.length)));

		List<String> promotionTitles = promotionsPage.getPromotionTitles();
		for (int i = 0; i < promotionTitles.size() - 1; i++) {
			verifyThat(promotionTitles.get(i).toLowerCase(), lessThanOrEqualTo(promotionTitles.get(i + 1).toLowerCase()));
		}

		renamePromotionContaining(promotionTitles.get(3), "aaa");

		final List<String> promotionsAgain = promotionsPage.getPromotionTitles();
		for (int i = 0; i < promotionsAgain.size() - 1; i++) {
			verifyThat(promotionsAgain.get(i).toLowerCase(), lessThanOrEqualTo(promotionsAgain.get(i + 1).toLowerCase()));
		}

		renamePromotionContaining(promotionTitles.get(3), promotionTitles.get(3));

		promotionsPage.promotionsSearchFilter().sendKeys("dog");
		verifyThat(promotionsPage, promotionsList(hasSize(1)));

		promotionsPage.clearPromotionsSearchFilter();
		promotionsPage.promotionsSearchFilter().sendKeys("wolf");
		verifyThat(promotionsPage, promotionsList(hasSize(2)));

		promotionsPage.clearPromotionsSearchFilter();
		promotionsPage.promotionsSearchFilter().sendKeys("pooch");
		verifyThat(promotionsPage, promotionsList(hasSize(3)));
		promotionTitles = promotionsPage.getPromotionTitles();
		for (int i = 0; i < promotionTitles.size() - 1; i++) {
			verifyThat(promotionTitles.get(i).toLowerCase(), lessThanOrEqualTo(promotionTitles.get(i + 1).toLowerCase()));
		}

		renamePromotionContaining("hound", "hound");

		promotionsPage.clearPromotionsSearchFilter();
		promotionsPage.promotionsSearchFilter().sendKeys("pooch");
		verifyThat(promotionsPage, promotionsList(hasSize(3)));

		goToDetails("pooch");
		promotionsDetailPage.trigger("pooch").removeAndWait();
		verifyThat(promotionsPage, triggerList(not(hasItem("pooch"))));
		promotionsDetailPage.backButton().click();

		promotionsPage.clearPromotionsSearchFilter();
		promotionsPage.promotionsSearchFilter().sendKeys("pooch");
		verifyThat(promotionsPage, promotionsList(hasSize(3)));

		verifyThat(promotionsPage.promotionsCategoryFilterValue(), is("All Types"));

		promotionsPage.selectPromotionsCategoryFilter("Spotlight");
		promotionsPage.clearPromotionsSearchFilter();
		verifyThat(promotionsPage.promotionsCategoryFilterValue(), is("Spotlight"));
		verifyThat(promotionsPage, promotionsList(hasSize(3)));

		promotionsPage.promotionsSearchFilter().sendKeys("woof");
		verifyThat(promotionsPage, promotionsList(hasSize(1)));

		promotionsPage.selectPromotionsCategoryFilter("Pin to Position");
		promotionsPage.clearPromotionsSearchFilter();
		verifyThat(promotionsPage.promotionsCategoryFilterValue(), is("Pin to Position"));
		verifyThat(promotionsPage, promotionsList(hasSize(2)));

		promotionsPage.promotionsSearchFilter().sendKeys("woof");
		verifyThat(promotionsPage, promotionsList(hasSize(1)));

		promotionsPage.clearPromotionsSearchFilter();
		verifyThat(promotionsPage, promotionsList(hasSize(2)));

		promotionsPage.selectPromotionsCategoryFilter("Dynamic Spotlight");
		promotionsPage.promotionsSearchFilter().sendKeys("wolf");
		verifyThat(promotionsPage, promotionsList(hasSize(2)));

		goToDetails("lupo");
		promotionsDetailPage.trigger("wolf").removeAndWait();
		verifyThat(promotionsPage, triggerList(not(hasItem("wolf"))));
		promotionsDetailPage.backButton().click();

		promotionsPage.clearPromotionsSearchFilter();
		promotionsPage.promotionsSearchFilter().sendKeys("wolf");
		verifyThat(promotionsPage, promotionsList(hasSize(2)));

		renamePromotionContaining("lupo", "lupo");

		promotionsPage.clearPromotionsSearchFilter();
		promotionsPage.promotionsSearchFilter().sendKeys("wolf");
		verifyThat(promotionsPage, promotionsList(hasSize(1)));

		goToDetails("hond");
		promotionsDetailPage.triggerAddBox().setAndSubmit("Rhodesian Ridgeback");
		promotionsDetailPage.waitForTriggerRefresh();
		verifyThat(promotionsPage, triggerList(hasItems("Rhodesian", "Ridgeback")));
		promotionsDetailPage.backButton().click();

		promotionsPage.clearPromotionsSearchFilter();
		promotionsPage.selectPromotionsCategoryFilter("Dynamic Spotlight");
		promotionsPage.promotionsSearchFilter().sendKeys("Rhodesian");
		verifyThat(promotionsPage, promotionsList(hasSize(1)));

		promotionsPage.selectPromotionsCategoryFilter("All Types");
		promotionsPage.clearPromotionsSearchFilter();
		// OP fails due to CCUK-2671
		promotionsPage.promotionsSearchFilter().sendKeys("Ridgeback");
		verifyThat(promotionsPage, promotionsList(hasSize(1)));
	}

	@Test
	public void testPromotionLanguages() {
		// TODO: IOD-4857
		assumeThat(config.getType(), equalTo(ApplicationType.ON_PREM));
		String[] languages = {"French", "Swahili", "Afrikaans"};
		String[] searchTerms = {"chien", "mbwa", "pooch"};
		Promotion[] promotions = {
				new SpotlightPromotion(Promotion.SpotlightType.HOTWIRE, "woof bark"),
				new PinToPositionPromotion(3, "swahili woof"),
				new DynamicPromotion(Promotion.SpotlightType.HOTWIRE, "hond wolf")
		};

		for (int i=0; i<languages.length; i++) {
			setUpPromotion(search(searchTerms[i], languages[i]), promotions[i]);
			verifyThat(promotionsDetailPage.getLanguage(), is(languages[i]));
		}
	}

	@Test
	public void testEditDynamicQuery() throws InterruptedException {
		search("kitty", "French").apply();
		SearchPage searchPage = getElementFactory().getSearchPage();
		final String firstSearchResult = searchPage.getSearchResult(1).getText();
		final String secondSearchResult = setUpPromotion(search("chat", "French"), new DynamicPromotion(Promotion.SpotlightType.TOP_PROMOTIONS, "meow")).get(0);

		promotionsDetailPage.triggerAddBox().setAndSubmit("purrr");
		promotionsDetailPage.trigger("meow").removeAndWait();
		search("purrr", "French").apply();
		verifyThat(searchPage.promotionsSummaryList(false).get(0), is(secondSearchResult));

		body.getSideNavBar().switchPage(NavBarTabId.PROMOTIONS);
//		promotionsPage.selectPromotionsCategoryFilter("All Types");
//		promotionsPage.loadOrFadeWait();
		goToDetails("meow");

		Editable queryText = promotionsDetailPage.queryText();
		verifyThat(queryText.getValue(), is("chat"));

		queryText.setValueAndWait("kitty");
		verifyThat(queryText.getValue(), is("kitty"));

		search("purrr", "French").apply();
		verifyThat(searchPage.promotionsSummaryList(false).get(0), is(firstSearchResult));

		getDriver().navigate().refresh();
		searchPage = getElementFactory().getSearchPage();
		verifyThat(searchPage.promotionsSummaryList(false).get(0), is(firstSearchResult));
	}

	@Test
	public void testPromotionCreationAndDeletionOnSecondWindow() {
		setUpPromotion(search("chien", "French"), new SpotlightPromotion(Promotion.SpotlightType.HOTWIRE, "woof bark"));

		promotionsDetailPage.backButton().click();
		final String url = getDriver().getCurrentUrl();
		final List<String> browserHandles = promotionsPage.createAndListWindowHandles();

		getDriver().switchTo().window(browserHandles.get(1));
		getDriver().get(url);
		final PromotionsPage secondPromotionsPage = getElementFactory().getPromotionsPage();
		assertThat("Navigated to promotions menu", secondPromotionsPage.promoteExistingButton().isDisplayed());

		getDriver().switchTo().window(browserHandles.get(0));
		setUpPromotion(search("rafiki", "Swahili"), new SpotlightPromotion(Promotion.SpotlightType.SPONSORED, "friend"));

		getDriver().switchTo().window(browserHandles.get(1));
		verifyThat(secondPromotionsPage, promotionsList(hasSize(2)));

		getDriver().switchTo().window(browserHandles.get(0));
		promotionsDetailPage.delete();

		getDriver().switchTo().window(browserHandles.get(1));
		verifyThat(secondPromotionsPage, promotionsList(hasSize(1)));

		promotionActionFactory.makeDelete("woof").apply();

		getDriver().switchTo().window(browserHandles.get(0));
		verifyThat(promotionsPage, containsText("There are no promotions..."));
	}

	@Test
	public void testCountSearchResultsWithPinToPositionInjected() {
		setUpPromotion(search("Lyon", "French"), new PinToPositionPromotion(13, "boeuf frites orange"));

		String[] queries = {"boeuf", "frites", "orange"};
		SearchPage searchPage;
		for (final String query : queries) {
			search(query, "French").apply();
			searchPage = getElementFactory().getSearchPage();
			final int firstPageStated = searchPage.countSearchResults();
			searchPage.forwardToLastPageButton().click();
			searchPage.waitForSearchLoadIndicatorToDisappear();
			final int numberOfPages = searchPage.getCurrentPageNumber();
			final int lastPageDocumentsCount = searchPage.visibleDocumentsCount();
			final int listedCount = (numberOfPages - 1) * searchPage.RESULTS_PER_PAGE + lastPageDocumentsCount;
			final int lastPageStated = searchPage.countSearchResults();
			verifyThat("count is the same across pages for " + query, firstPageStated, is(lastPageStated));
			verifyThat("count is correct for " + query, lastPageStated, is(listedCount));
		}
	}
}