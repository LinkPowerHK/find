package com.hp.autonomy.frontend.find.idol.savedsearches.snapshot;

import com.autonomy.aci.client.services.AciErrorException;
import com.hp.autonomy.frontend.find.core.savedsearches.EmbeddableIndex;
import com.hp.autonomy.frontend.find.core.savedsearches.snapshot.SavedSnapshot;
import com.hp.autonomy.frontend.find.core.savedsearches.snapshot.SavedSnapshotController;
import com.hp.autonomy.frontend.find.core.savedsearches.snapshot.SavedSnapshotService;
import com.hp.autonomy.searchcomponents.core.search.DocumentsService;
import com.hp.autonomy.searchcomponents.idol.search.IdolQueryRestrictions;
import com.hp.autonomy.searchcomponents.idol.search.IdolSearchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping(SavedSnapshotController.PATH)
public class IdolSavedSnapshotController extends SavedSnapshotController<String, IdolSearchResult, AciErrorException> {

    @Autowired
    public IdolSavedSnapshotController(final SavedSnapshotService service, final DocumentsService<String, IdolSearchResult, AciErrorException> documentsService) {
        super(service, documentsService);
    }

    private List<String> getDatabases(final Iterable<EmbeddableIndex> indexes) {
        final List<String> databases = new ArrayList<>();

        for (final EmbeddableIndex index : indexes) {
            databases.add(index.getName());
        }

        return databases;
    }

    @Override
    protected String getStateToken(final SavedSnapshot snapshot) throws AciErrorException {
        final IdolQueryRestrictions.Builder queryRestrictionsBuilder = new IdolQueryRestrictions.Builder()
                .setDatabases(getDatabases(snapshot.getIndexes()))
                .setQueryText(getQueryText(snapshot)).setFieldText(getFieldText(snapshot.getParametricValues()))
                .setMaxDate(snapshot.getMaxDate())
                .setMinDate(snapshot.getMinDate());

        return documentsService.getStateToken(queryRestrictionsBuilder.build(), Integer.MAX_VALUE);
    }
}
