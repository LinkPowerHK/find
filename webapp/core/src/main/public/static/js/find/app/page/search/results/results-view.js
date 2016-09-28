/*
 * Copyright 2015-2016 Hewlett-Packard Development Company, L.P.
 * Licensed under the MIT License (the "License"); you may not use this file except in compliance with the License.
 */
define([
    'backbone',
    'jquery',
    'underscore',
    'find/app/vent',
    'find/app/model/document-model',
    'find/app/model/promotions-collection',
    'find/app/page/search/sort-view',
    'find/app/page/search/results/results-number-view',
    'find/app/page/search/results/result-rendering/result-renderer',
    'find/app/page/search/results/result-rendering/result-renderer-config',
    'find/app/util/view-server-client',
    'find/app/util/events',
    'find/app/page/search/results/add-links-to-summary',
    'find/app/configuration',
    'find/app/util/generate-error-support-message',
    'text!find/templates/app/page/search/results/results-view.html',
    'text!find/templates/app/page/loading-spinner.html',
    'moment',
    'i18n!find/nls/bundle',
    'i18n!find/nls/indexes'
], function(Backbone, $, _, vent, DocumentModel, PromotionsCollection, SortView, ResultsNumberView,
            ResultRenderer, resultsRendererConfig, viewClient, events, addLinksToSummary, configuration,
            generateErrorHtml, html, loadingSpinnerTemplate, moment, i18n, i18n_indexes) {
    "use strict";

    var SCROLL_INCREMENT = 30;
    var INFINITE_SCROLL_POSITION_PIXELS = 500;

    function infiniteScroll() {
        var resultsPresent = this.documentsCollection.size() > 0 && this.fetchStrategy.validateQuery(this.queryModel);

        if(resultsPresent && this.resultsFinished && !this.endOfResults) {
            this.start = this.maxResults + 1;
            this.maxResults += SCROLL_INCREMENT;

            this.loadData(true);

            events().page(this.maxResults / SCROLL_INCREMENT);
        }
    }

    return Backbone.View.extend({
        loadingTemplate: _.template(loadingSpinnerTemplate)({i18n: i18n, large: true}),
        messageTemplate: _.template('<div class="result-message span10"><%-message%></div>'),

        events: function() {
            var events = {
                'click .document-detail-mode [data-cid]': function(e) {
                    var $target = $(e.currentTarget);
                    var cid = $target.data('cid');
                    var isPromotion = $target.closest('.main-results-list').hasClass('promotions');
                    var collection = isPromotion ? this.promotionsCollection : this.documentsCollection;
                    var model = collection.get(cid);
                    vent.navigateToDetailRoute(model);
                },
                'click .similar-documents-trigger': function(event) {
                    event.stopPropagation();
                    var cid = $(event.target).closest('[data-cid]').data('cid');
                    var documentModel = this.documentsCollection.get(cid);
                    if(!documentModel) {
                        documentModel = this.promotionsCollection.get(cid);
                    }
                    vent.navigateToSuggestRoute(documentModel);
                }
            };

            var selector = configuration().directAccessLink ? '.preview-link' : '.preview-mode [data-cid]';
            events['click ' + selector] = 'openPreview';

            return events;
        },

        initialize: function(options) {
            this.fetchStrategy = options.fetchStrategy;

            this.queryModel = options.queryModel;
            this.showPromotions = this.fetchStrategy.promotions(this.queryModel) && !options.hidePromotions;
            this.documentsCollection = options.documentsCollection;

            this.indexesCollection = options.indexesCollection;
            this.scrollModel = options.scrollModel;

            // Preview mode is enabled when a preview mode model is provided
            this.previewModeModel = options.previewModeModel;

            if(this.indexesCollection) {
                this.selectedIndexesCollection = options.queryState.selectedIndexes;
            }

            this.resultRenderer = new ResultRenderer({
                config: resultsRendererConfig
            });

            if(this.showPromotions) {
                this.promotionsCollection = new PromotionsCollection();
            }

            this.sortView = new SortView({
                queryModel: this.queryModel
            });

            this.resultsNumberView = new ResultsNumberView({
                documentsCollection: this.documentsCollection
            });

            this.listenTo(this.queryModel, 'change refresh', this.refreshResults);

            this.infiniteScroll = _.debounce(infiniteScroll, 500, true);

            this.listenTo(this.scrollModel, 'change', function() {
                if(this.scrollModel.get('scrollTop') > this.scrollModel.get('scrollHeight') - INFINITE_SCROLL_POSITION_PIXELS - this.scrollModel.get('innerHeight')) {
                    this.infiniteScroll();
                }
            });

            if(this.previewModeModel) {
                this.listenTo(this.previewModeModel, 'change:document', this.updateSelectedDocument);
            }
        },

        render: function() {
            this.$el.html(html);

            this.$loadingSpinner = $(this.loadingTemplate);

            this.$el.find('.results').after(this.$loadingSpinner);

            this.sortView.setElement(this.$('.sort-container')).render();
            this.resultsNumberView.setElement(this.$('.results-number-container')).render();

            if(this.showPromotions) {
                this.listenTo(this.promotionsCollection, 'add', function(model) {
                    this.formatResult(model, true);
                });

                this.listenTo(this.promotionsCollection, 'sync', function() {
                    this.promotionsFinished = true;
                    this.clearLoadingSpinner();
                });

                // TODO: We're basically ignoring promotions errors here -- implement robust logging procedure.
                // The Find User shouldn't hear about promotions, but the way we are doing it now, the DataAdmin or
                // SysAdmin may never find out that promotions-related errors are affecting Users' searches.
                this.listenTo(this.promotionsCollection, 'error', function() {
                    this.promotionsFinished = true;
                    this.clearLoadingSpinner();
                });
            }

            this.listenTo(this.documentsCollection, 'add', function(model) {
                this.formatResult(model, false);
            });

            this.listenTo(this.documentsCollection, 'sync reset', function() {
                this.resultsFinished = true;
                this.clearLoadingSpinner();

                this.endOfResults = this.maxResults >= this.documentsCollection.totalResults;

                if(this.endOfResults && !this.documentsCollection.isEmpty()) {
                    this.$('.main-results-content .results').append(this.messageTemplate({message: i18n["search.noMoreResults"]}));
                } else if(this.documentsCollection.isEmpty()) {
                    this.$('.main-results-content .results').append(this.messageTemplate({message: i18n["search.noResults"]}));
                }
            });

            this.listenTo(this.documentsCollection, 'error', function(collection, xhr) {
                this.resultsFinished = true;
                this.clearLoadingSpinner();
                this.handleError(xhr);
            });

            this.refreshResults();

            if(this.entityCollection) {
                this.updateEntityHighlighting();
            }

            if(this.previewModeModel) {
                this.$('.main-results-content').addClass('preview-mode');
                this.updateSelectedDocument();
            } else {
                this.$('.main-results-content').addClass('document-detail-mode');
            }
        },

        refreshResults: function() {
            if(this.fetchStrategy.validateQuery(this.queryModel)) {
                if(this.fetchStrategy.waitForIndexes(this.queryModel)) {
                    this.$loadingSpinner.addClass('hide');
                    this.$('.main-results-content .results').html(this.messageTemplate({message: i18n_indexes['search.error.noIndexes']}));
                } else {
                    this.endOfResults = false;
                    this.start = 1;
                    this.maxResults = SCROLL_INCREMENT;
                    this.loadData(false);
                    this.$('.main-results-content .promotions').empty();

                    if(this.$loadingSpinner) {
                        this.$loadingSpinner.removeClass('hide');
                    }
                    this.toggleError(false);
                    this.$('.main-results-content .results').empty();
                }
            }
        },

        clearLoadingSpinner: function() {
            if(this.resultsFinished && this.promotionsFinished || !this.showPromotions) {
                this.$loadingSpinner.addClass('hide');
            }
        },

        updateSelectedDocument: function() {
            var documentModel = this.previewModeModel.get('document');
            this.$('.main-results-container').removeClass('selected-document');

            if(documentModel !== null) {
                this.$('.main-results-container[data-cid="' + documentModel.cid + '"]').addClass('selected-document');
            }
        },

        formatResult: function(model, isPromotion) {
            var $newResult = this.resultRenderer.getResult(model, isPromotion, Boolean(this.previewModeModel), configuration().directAccessLink);

            if(isPromotion) {
                this.$('.main-results-content .promotions').append($newResult);
            } else {
                this.$('.main-results-content .results').append($newResult);
            }
        },

        generateErrorMessage: function(xhr) {
            if(xhr.responseJSON) {
                return generateErrorHtml({
                    errorDetails: xhr.responseJSON.message,
                    errorUUID: xhr.responseJSON.uuid,
                    errorLookup: xhr.responseJSON.backendErrorCode
                });
            } else {
                return generateErrorHtml();
            }
        },

        handleError: function(xhr) {
            this.toggleError(true);
            this.$('.main-results-content .results-view-error').empty().append(this.generateErrorMessage(xhr));
        },

        toggleError: function(on) {
            this.$('.main-results-content .promotions').toggleClass('hide', on);
            this.$('.main-results-content .results').toggleClass('hide', on);
            this.$('.main-results-content .results-view-error').toggleClass('hide', !on);
        },

        loadData: function(infiniteScroll) {
            if(this.$loadingSpinner) {
                this.$loadingSpinner.removeClass('hide');
            }

            this.resultsFinished = false;

            var requestData = _.extend({
                start: this.start,
                max_results: this.maxResults,
                sort: this.queryModel.get('sort'),
                auto_correct: this.queryModel.get('autoCorrect'),
                queryType: 'MODIFIED'
            }, this.fetchStrategy.requestParams(this.queryModel, infiniteScroll));

            this.documentsCollection.fetch({
                data: requestData,
                reset: false,
                remove: !infiniteScroll,
                error: function(collection) {
                    // if returns an error remove previous models from documentsCollection
                    if(collection) {
                        collection.reset();
                    }
                },
                success: function() {
                    if(this.indexesCollection && this.documentsCollection.warnings && this.documentsCollection.warnings.invalidDatabases) {
                        // Invalid databases have been deleted from IDOL; mark them as such in the indexes collection
                        this.documentsCollection.warnings.invalidDatabases.forEach(function(name) {
                            var indexModel = this.indexesCollection.findWhere({name: name});

                            if(indexModel) {
                                indexModel.set('deleted', true);
                            }

                            this.selectedIndexesCollection.remove({name: name});
                        }.bind(this));
                    }
                }.bind(this)
            });

            if(!infiniteScroll && this.showPromotions) {
                this.promotionsFinished = false;

                var promotionsRequestData = _.extend({
                    start: this.start,
                    max_results: this.maxResults,
                    sort: this.queryModel.get('sort'),
                    queryType: 'PROMOTIONS'
                }, this.fetchStrategy.promotionsRequestParams(this.queryModel, infiniteScroll));

                this.promotionsCollection.fetch({
                    data: promotionsRequestData,
                    reset: false
                }, this);

                // we're not scrolling, so should be a new search
                events().reset(requestData.text);
            }
        },

        openPreview: function(e) {
            var $target = $(e.currentTarget).closest('.main-results-container');

            if($target.hasClass('selected-document')) {
                // disable preview mode
                this.previewModeModel.set({document: null});
            } else {
                //enable/choose another preview view
                var cid = $target.data('cid');
                var isPromotion = $target.closest('.main-results-list').hasClass('promotions');
                var collection = isPromotion ? this.promotionsCollection : this.documentsCollection;
                var model = collection.get(cid);
                this.previewModeModel.set({document: model});

                if(!isPromotion) {
                    events().preview(collection.indexOf(model) + 1);
                }
            }
        },

        remove: function() {
            this.sortView.remove();
            this.resultsNumberView.remove();
            Backbone.View.prototype.remove.call(this);
        }
    });
});