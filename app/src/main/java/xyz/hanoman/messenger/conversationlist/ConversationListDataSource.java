package xyz.hanoman.messenger.conversationlist;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.signal.core.util.logging.Log;
import org.signal.paging.PagedDataSource;
import xyz.hanoman.messenger.conversationlist.model.Conversation;
import xyz.hanoman.messenger.conversationlist.model.ConversationReader;
import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.database.ThreadDatabase;
import xyz.hanoman.messenger.database.model.ThreadRecord;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.recipients.Recipient;
import xyz.hanoman.messenger.util.Stopwatch;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

abstract class ConversationListDataSource implements PagedDataSource<Conversation> {

  private static final String TAG = Log.tag(ConversationListDataSource.class);

  protected final ThreadDatabase threadDatabase;

  protected ConversationListDataSource(@NonNull Context context) {
    this.threadDatabase = DatabaseFactory.getThreadDatabase(context);
  }

  public static ConversationListDataSource create(@NonNull Context context, boolean isArchived) {
    if (!isArchived) return new UnarchivedConversationListDataSource(context);
    else             return new ArchivedConversationListDataSource(context);
  }

  @Override
  public int size() {
    long startTime = System.currentTimeMillis();
    int  count     = getTotalCount();

    Log.d(TAG, "[size(), " + getClass().getSimpleName() + "] " + (System.currentTimeMillis() - startTime) + " ms");
    return count;
  }

  @Override
  public @NonNull List<Conversation> load(int start, int length, @NonNull CancellationSignal cancellationSignal) {
    Stopwatch stopwatch = new Stopwatch("load(" + start + ", " + length + "), " + getClass().getSimpleName());

    List<Conversation> conversations  = new ArrayList<>(length);
    List<Recipient>    recipients     = new LinkedList<>();

    try (ConversationReader reader = new ConversationReader(getCursor(start, length))) {
      ThreadRecord record;
      while ((record = reader.getNext()) != null && !cancellationSignal.isCanceled()) {
        conversations.add(new Conversation(record));
        recipients.add(record.getRecipient());
      }
    }

    stopwatch.split("cursor");

    ApplicationDependencies.getRecipientCache().addToCache(recipients);

    stopwatch.split("cache-recipients");

    stopwatch.stop(TAG);

    return conversations;
  }

  protected abstract int getTotalCount();
  protected abstract Cursor getCursor(long offset, long limit);

  private static class ArchivedConversationListDataSource extends ConversationListDataSource {

    ArchivedConversationListDataSource(@NonNull Context context) {
      super(context);
    }

    @Override
    protected int getTotalCount() {
      return threadDatabase.getArchivedConversationListCount();
    }

    @Override
    protected Cursor getCursor(long offset, long limit) {
      return threadDatabase.getArchivedConversationList(offset, limit);
    }
  }

  @VisibleForTesting
  static class UnarchivedConversationListDataSource extends ConversationListDataSource {

    private int totalCount;
    private int pinnedCount;
    private int archivedCount;
    private int unpinnedCount;

    UnarchivedConversationListDataSource(@NonNull Context context) {
      super(context);
    }

    @Override
    protected int getTotalCount() {
      int unarchivedCount = threadDatabase.getUnarchivedConversationListCount();

      pinnedCount   = threadDatabase.getPinnedConversationListCount();
      archivedCount = threadDatabase.getArchivedConversationListCount();
      unpinnedCount = unarchivedCount - pinnedCount;
      totalCount    = unarchivedCount;

      if (archivedCount != 0) {
        totalCount++;
      }

      if (pinnedCount != 0) {
        if (unpinnedCount != 0) {
          totalCount += 2;
        } else {
          totalCount += 1;
        }
      }

      return totalCount;
    }

    @Override
    protected Cursor getCursor(long offset, long limit) {
      List<Cursor> cursors       = new ArrayList<>(5);
      long         originalLimit = limit;

      if (offset == 0 && hasPinnedHeader()) {
        MatrixCursor pinnedHeaderCursor = new MatrixCursor(ConversationReader.HEADER_COLUMN);
        pinnedHeaderCursor.addRow(ConversationReader.PINNED_HEADER);
        cursors.add(pinnedHeaderCursor);
        limit--;
      }

      Cursor pinnedCursor = threadDatabase.getUnarchivedConversationList(true, offset, limit);
      cursors.add(pinnedCursor);
      limit -= pinnedCursor.getCount();

      if (offset == 0 && hasUnpinnedHeader()) {
        MatrixCursor unpinnedHeaderCursor = new MatrixCursor(ConversationReader.HEADER_COLUMN);
        unpinnedHeaderCursor.addRow(ConversationReader.UNPINNED_HEADER);
        cursors.add(unpinnedHeaderCursor);
        limit--;
      }

      long   unpinnedOffset = Math.max(0, offset - pinnedCount - getHeaderOffset());
      Cursor unpinnedCursor = threadDatabase.getUnarchivedConversationList(false, unpinnedOffset, limit);
      cursors.add(unpinnedCursor);

      if (offset + originalLimit >= totalCount && hasArchivedFooter()) {
        MatrixCursor archivedFooterCursor = new MatrixCursor(ConversationReader.ARCHIVED_COLUMNS);
        archivedFooterCursor.addRow(ConversationReader.createArchivedFooterRow(archivedCount));
        cursors.add(archivedFooterCursor);
      }

      return new MergeCursor(cursors.toArray(new Cursor[]{}));
    }

    @VisibleForTesting
    int getHeaderOffset() {
      return (hasPinnedHeader() ? 1 : 0) + (hasUnpinnedHeader() ? 1 : 0);
    }

    @VisibleForTesting
    boolean hasPinnedHeader() {
      return pinnedCount != 0;
    }

    @VisibleForTesting
    boolean hasUnpinnedHeader() {
      return hasPinnedHeader() && unpinnedCount != 0;
    }

    @VisibleForTesting
    boolean hasArchivedFooter() {
      return archivedCount != 0;
    }
  }
}
