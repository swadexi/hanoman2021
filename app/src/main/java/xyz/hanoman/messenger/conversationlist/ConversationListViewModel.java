package xyz.hanoman.messenger.conversationlist;

import android.app.Application;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.signal.core.util.logging.Log;
import org.signal.paging.PagedData;
import org.signal.paging.PagingConfig;
import org.signal.paging.PagingController;
import xyz.hanoman.messenger.conversationlist.model.Conversation;
import xyz.hanoman.messenger.conversationlist.model.SearchResult;
import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.database.DatabaseObserver;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.megaphone.Megaphone;
import xyz.hanoman.messenger.megaphone.MegaphoneRepository;
import xyz.hanoman.messenger.megaphone.Megaphones;
import xyz.hanoman.messenger.search.SearchRepository;
import xyz.hanoman.messenger.util.Debouncer;
import xyz.hanoman.messenger.util.Util;
import xyz.hanoman.messenger.util.livedata.LiveDataUtil;
import xyz.hanoman.messenger.util.paging.Invalidator;

import java.util.List;

class ConversationListViewModel extends ViewModel {

  private static final String TAG = Log.tag(ConversationListViewModel.class);

  private static boolean coldStart = true;

  private final MutableLiveData<Megaphone>     megaphone;
  private final MutableLiveData<SearchResult>  searchResult;
  private final PagedData<Conversation>        pagedData;
  private final LiveData<Boolean>              hasNoConversations;
  private final SearchRepository               searchRepository;
  private final MegaphoneRepository            megaphoneRepository;
  private final Debouncer                      debouncer;
  private final DatabaseObserver.Observer      observer;
  private final Invalidator                    invalidator;

  private String lastQuery;
  private int    pinnedCount;

  private ConversationListViewModel(@NonNull Application application, @NonNull SearchRepository searchRepository, boolean isArchived) {
    this.megaphone           = new MutableLiveData<>();
    this.searchResult        = new MutableLiveData<>();
    this.searchRepository    = searchRepository;
    this.megaphoneRepository = ApplicationDependencies.getMegaphoneRepository();
    this.debouncer           = new Debouncer(300);
    this.invalidator         = new Invalidator();
    this.pagedData           = PagedData.create(ConversationListDataSource.create(application, isArchived),
                                                new PagingConfig.Builder()
                                                                .setPageSize(15)
                                                                .setBufferPages(2)
                                                                .build());
    this.observer            = () -> {
      if (!TextUtils.isEmpty(getLastQuery())) {
        searchRepository.query(getLastQuery(), searchResult::postValue);
      }
      pagedData.getController().onDataInvalidated();
    };

    this.hasNoConversations = LiveDataUtil.mapAsync(pagedData.getData(), conversations -> {
      pinnedCount = DatabaseFactory.getThreadDatabase(application).getPinnedConversationListCount();

      if (conversations.size() > 0) {
        return false;
      } else {
        return DatabaseFactory.getThreadDatabase(application).getArchivedConversationListCount() == 0;
      }
    });

    ApplicationDependencies.getDatabaseObserver().registerConversationListObserver(observer);
  }

  public LiveData<Boolean> hasNoConversations() {
    return hasNoConversations;
  }

  @NonNull LiveData<SearchResult> getSearchResult() {
    return searchResult;
  }

  @NonNull LiveData<Megaphone> getMegaphone() {
    return megaphone;
  }

  @NonNull LiveData<List<Conversation>> getConversationList() {
    return pagedData.getData();
  }

  @NonNull PagingController getPagingController() {
    return pagedData.getController();
  }

  public int getPinnedCount() {
    return pinnedCount;
  }

  void onVisible() {
    megaphoneRepository.getNextMegaphone(megaphone::postValue);

    if (!coldStart) {
      ApplicationDependencies.getDatabaseObserver().notifyConversationListListeners();
    }

    coldStart = false;
  }

  void onMegaphoneCompleted(@NonNull Megaphones.Event event) {
    megaphone.postValue(null);
    megaphoneRepository.markFinished(event);
  }

  void onMegaphoneSnoozed(@NonNull Megaphones.Event event) {
    megaphoneRepository.markSeen(event);
    megaphone.postValue(null);
  }

  void onMegaphoneVisible(@NonNull Megaphone visible) {
    megaphoneRepository.markVisible(visible.getEvent());
  }

  void updateQuery(String query) {
    lastQuery = query;
    debouncer.publish(() -> searchRepository.query(query, result -> {
      Util.runOnMain(() -> {
        if (query.equals(lastQuery)) {
          searchResult.setValue(result);
        }
      });
    }));
  }

  private @NonNull String getLastQuery() {
    return lastQuery == null ? "" : lastQuery;
  }

  @Override
  protected void onCleared() {
    invalidator.invalidate();
    debouncer.clear();
    ApplicationDependencies.getDatabaseObserver().unregisterObserver(observer);
  }

  public static class Factory extends ViewModelProvider.NewInstanceFactory {

    private final boolean isArchived;

    public Factory(boolean isArchived) {
      this.isArchived = isArchived;
    }

    @Override
    public @NonNull<T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new ConversationListViewModel(ApplicationDependencies.getApplication(), new SearchRepository(), isArchived));
    }
  }
}
