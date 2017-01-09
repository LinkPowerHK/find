package com.hp.autonomy.frontend.find.core.search;

import com.hp.autonomy.searchcomponents.core.search.QueryRestrictions;
import com.hp.autonomy.searchcomponents.core.search.SearchRequest;

import java.beans.ConstructorProperties;
import java.io.Serializable;

public class SearchRequestNew<S extends Serializable> extends SearchRequest{
    protected int weight = 50;

    public int getWeight(){return this.weight;}

    public void setWeight(int weight){this.weight = weight;}

    @ConstructorProperties({"queryRestrictions", "start", "maxResults", "summary", "summaryCharacters", "sort", "highlight", "autoCorrect", "weight", "queryType"})
    public SearchRequestNew(QueryRestrictions<S> queryRestrictions, int start, int maxResults, String summary, Integer summaryCharacters, String sort, boolean highlight, boolean autoCorrect, int weight, SearchRequest.QueryType queryType) {
        super(queryRestrictions,start,maxResults,summary,summaryCharacters,sort,highlight,autoCorrect,queryType);
        this.weight = weight;
    }
}
