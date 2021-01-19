package xyz.hanoman.messenger.preferences;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProviders;

import org.signal.core.util.concurrent.SignalExecutors;
import xyz.hanoman.messenger.R;
import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.database.MediaDatabase;
import xyz.hanoman.messenger.preferences.widgets.StorageGraphView;

import java.util.Arrays;

public class ApplicationPreferencesViewModel extends ViewModel {

  private final MutableLiveData<StorageGraphView.StorageBreakdown> storageBreakdown = new MutableLiveData<>();

  LiveData<StorageGraphView.StorageBreakdown> getStorageBreakdown() {
    return storageBreakdown;
  }

  static ApplicationPreferencesViewModel getApplicationPreferencesViewModel(@NonNull FragmentActivity activity) {
    return ViewModelProviders.of(activity).get(ApplicationPreferencesViewModel.class);
  }

  void refreshStorageBreakdown(@NonNull Context context) {
    SignalExecutors.BOUNDED.execute(() -> {
      MediaDatabase.StorageBreakdown breakdown = DatabaseFactory.getMediaDatabase(context)
                                                                .getStorageBreakdown();

      StorageGraphView.StorageBreakdown latestStorageBreakdown = new StorageGraphView.StorageBreakdown(Arrays.asList(
        new StorageGraphView.Entry(ContextCompat.getColor(context, R.color.storage_color_photos), breakdown.getPhotoSize()),
        new StorageGraphView.Entry(ContextCompat.getColor(context, R.color.storage_color_videos), breakdown.getVideoSize()),
        new StorageGraphView.Entry(ContextCompat.getColor(context, R.color.storage_color_files), breakdown.getDocumentSize()),
        new StorageGraphView.Entry(ContextCompat.getColor(context, R.color.storage_color_audio), breakdown.getAudioSize())
      ));

      storageBreakdown.postValue(latestStorageBreakdown);
    });
  }
}
