package com.hp.autonomy.frontend.find.idol.search;

import com.hp.autonomy.searchcomponents.idol.search.IdolDocumentService;
import com.autonomy.aci.client.services.AciErrorException;
import com.autonomy.aci.client.services.AciService;
import com.autonomy.aci.client.util.AciParameters;
import com.hp.autonomy.frontend.configuration.ConfigService;
import com.hp.autonomy.frontend.find.core.search.SearchRequestNew;
import com.hp.autonomy.searchcomponents.core.search.SearchRequest;
import com.hp.autonomy.idolutils.processors.AciResponseJaxbProcessorFactory;
import com.hp.autonomy.searchcomponents.idol.configuration.IdolSearchCapable;
import com.hp.autonomy.searchcomponents.idol.search.HavenSearchAciParameterHandler;
import com.hp.autonomy.searchcomponents.idol.search.IdolSearchResult;
import com.hp.autonomy.searchcomponents.idol.search.QueryResponseParser;
import com.hp.autonomy.types.idol.QueryResponseData;
import com.hp.autonomy.types.requests.Documents;
import com.hp.autonomy.types.requests.idol.actions.query.QueryActions;
import com.hp.autonomy.types.requests.idol.actions.query.params.QueryParams;
import com.hp.autonomy.types.requests.qms.actions.query.params.QmsQueryParams;

public class IdolDocumentServiceNew extends IdolDocumentService {
    public IdolDocumentServiceNew(ConfigService<? extends IdolSearchCapable> configService, HavenSearchAciParameterHandler parameterHandler, QueryResponseParser queryResponseParser, AciService contentAciService, AciService qmsAciService, AciResponseJaxbProcessorFactory aciResponseProcessorFactory) {
        super(configService,parameterHandler,queryResponseParser,contentAciService,qmsAciService,aciResponseProcessorFactory);
    }
    public Documents<IdolSearchResult> queryTextIndex(SearchRequestNew<String> searchRequestNew) throws AciErrorException {
        SearchRequest.QueryType queryType = searchRequestNew.getQueryType();
        boolean useQms = this.qmsEnabled() && queryType != SearchRequest.QueryType.RAW;
        return this.queryTextIndex(useQms?this.qmsAciService:this.contentAciService, searchRequestNew, queryType == SearchRequest.QueryType.PROMOTIONS);
    }
    private boolean qmsEnabled() {
        return ((IdolSearchCapable)this.configService.getConfig()).getQueryManipulation().isEnabled();
    }
    private Documents<IdolSearchResult> queryTextIndex(final AciService aciService, SearchRequestNew<String> searchRequestNew, boolean promotions) {
        AciParameters aciParameters = new AciParameters(QueryActions.Query.name());
        this.parameterHandler.addSearchRestrictions(aciParameters, searchRequestNew.getQueryRestrictions());
        this.parameterHandler.addSearchOutputParameters(aciParameters, searchRequestNew);
        if(searchRequestNew.getQueryType() != SearchRequestNew.QueryType.RAW) {
            this.parameterHandler.addQmsParameters(aciParameters, searchRequestNew.getQueryRestrictions());
        }

        if(searchRequestNew.isAutoCorrect()) {
            aciParameters.add(QueryParams.SpellCheck.name(), Boolean.valueOf(true));
        }

        if(promotions) {
            aciParameters.add(QmsQueryParams.Promotions.name(), Boolean.valueOf(true));
        }

        QueryResponseData responseData = super.executeQuery(aciService, aciParameters);
        return this.queryResponseParser.parseQueryResults(searchRequestNew, aciParameters, responseData, new IdolDocumentService.QueryExecutor() {
            public QueryResponseData execute(AciParameters parameters) {
                return IdolDocumentServiceNew.super.executeQuery(aciService, parameters);
            }
        });
    }
    protected QueryResponseData executeQuery(AciService aciService, AciParameters aciParameters) {
        return (QueryResponseData)aciService.executeAction(aciParameters, this.queryResponseProcessor);
    }
}