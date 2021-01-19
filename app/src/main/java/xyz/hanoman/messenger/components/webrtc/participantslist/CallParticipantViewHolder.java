package xyz.hanoman.messenger.components.webrtc.participantslist;

import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import xyz.hanoman.messenger.R;
import xyz.hanoman.messenger.util.viewholders.RecipientViewHolder;

public class CallParticipantViewHolder extends RecipientViewHolder<CallParticipantViewState> {

  private final ImageView videoMuted;
  private final ImageView audioMuted;

  public CallParticipantViewHolder(@NonNull View itemView) {
    super(itemView, null);

    videoMuted = itemView.findViewById(R.id.call_participant_video_muted);
    audioMuted = itemView.findViewById(R.id.call_participant_audio_muted);
  }

  @Override
  public void bind(@NonNull CallParticipantViewState model) {
    super.bind(model);

    videoMuted.setVisibility(model.getVideoMutedVisibility());
    audioMuted.setVisibility(model.getAudioMutedVisibility());
  }
}
