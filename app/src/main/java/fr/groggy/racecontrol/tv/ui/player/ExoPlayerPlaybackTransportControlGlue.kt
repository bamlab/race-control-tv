package fr.groggy.racecontrol.tv.ui.player

import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.leanback.media.PlaybackTransportControlGlue
import androidx.leanback.media.PlayerAdapter
import androidx.leanback.widget.*
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.analytics.AnalyticsListener.EventTime
import com.google.android.exoplayer2.ext.leanback.LeanbackPlayerAdapter
import com.google.android.exoplayer2.source.MediaLoadData
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.text.Cue
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import fr.groggy.racecontrol.tv.R
import fr.groggy.racecontrol.tv.ui.channel.playback.ChannelPlaybackActivity
import fr.groggy.racecontrol.tv.ui.channel.playback.ChannelPlaybackFragment
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ExoPlayerPlaybackTransportControlGlue(
    private val activity: FragmentActivity,
    player: ExoPlayer,
    private val trackSelector: DefaultTrackSelector
) : PlaybackTransportControlGlue<LeanbackPlayerAdapter>(
    activity,
    LeanbackPlayerAdapter(activity, player, 1_000)
), AnalyticsListener {

    companion object {
        private val TAG = ExoPlayerPlaybackTransportControlGlue::class.simpleName

        private const val DEFAULT_SEEK_OFFSET = 15_000L
    }

    private val rewindAction = PlaybackControlsRow.RewindAction(activity)
    private val fastFormatAction = PlaybackControlsRow.FastForwardAction(activity)
    private val selectAudioAction = Action(
        Action.NO_ID,
        activity.getString(R.string.audio_selection_dialog_title),
        null,
        ContextCompat.getDrawable(context, androidx.leanback.R.drawable.lb_ic_search_mic_out)
    )
    private val switchChannelAction = Action(
        Action.NO_ID,
        activity.getString(R.string.channel_selection_switch_channel),
        null,
        ContextCompat.getDrawable(context, R.drawable.ic_switch_channel)
    )
    private val resolutionSelectionAction = Action(
        Action.NO_ID,
        activity.getText(R.string.video_selection_dialog_title),
        null,
        ContextCompat.getDrawable(context, R.drawable.ic_video_settings)
    )
    private val closedCaptionAction = PlaybackControlsRow.ClosedCaptioningAction(activity)

    private val closedCaptionsTextView: TextView by lazy {
        activity.findViewById(R.id.closed_captions)
    }

    private var currentVideoFormat: Format? = null
    private var currentAudioFormat: Format? = null

    init {
        player.addAnalyticsListener(this)
        isSeekEnabled = true
        isControlsOverlayAutoHideEnabled = true
    }

    override fun onCreatePrimaryActions(adapter: ArrayObjectAdapter) {
        Log.d(TAG, "onCreatePrimaryActions")
        adapter.apply {
            super.onCreatePrimaryActions(this)
            add(rewindAction)
            add(fastFormatAction)
            add(selectAudioAction)
            add(switchChannelAction)
            add(resolutionSelectionAction)
            add(closedCaptionAction)
        }
    }

    override fun onActionClicked(action: Action) {
        Log.d(TAG, "onActionClicked")
        when (action) {
            rewindAction -> playerAdapter.seekOffset(-DEFAULT_SEEK_OFFSET)
            fastFormatAction -> playerAdapter.seekOffset(DEFAULT_SEEK_OFFSET)
            selectAudioAction -> openAudioSelectionDialog()
            closedCaptionAction -> toggleClosedCaptions()
            resolutionSelectionAction -> openResolutionSelectionDialog()
            switchChannelAction -> openChannelSwitchDialog()
            else -> super.onActionClicked(action)
        }
    }

    private fun openChannelSwitchDialog() {
        val sessionId = ChannelPlaybackFragment.findSessionId(activity) ?: return
        val contentId = ChannelPlaybackFragment.findContentId(activity) ?: return

        ChannelSelectionDialog.newInstance(
            sessionId,
            contentId
        ).show(activity.supportFragmentManager)
    }

    private fun openResolutionSelectionDialog() {
        trackSelector.currentMappedTrackInfo?.let {
            ResolutionSelectionDialog(it)
                .setResolutionSelectedListener { width, height ->
                    val newParams = trackSelector.buildUponParameters()
                        .setMaxVideoSize(width, height)
                    trackSelector.setParameters(newParams)
                }.show(activity.supportFragmentManager, null)
        }
    }

    private fun toggleClosedCaptions() {
        if (closedCaptionAction.index == PlaybackControlsRow.ClosedCaptioningAction.INDEX_OFF) {
            closedCaptionAction.index = PlaybackControlsRow.ClosedCaptioningAction.INDEX_ON
            closedCaptionsTextView.visibility = View.VISIBLE
        } else {
            closedCaptionAction.index = PlaybackControlsRow.ClosedCaptioningAction.INDEX_OFF
            closedCaptionsTextView.visibility = View.GONE
        }
    }

    private fun openAudioSelectionDialog() {
        trackSelector.currentMappedTrackInfo?.let {
            val audio = it.getTrackGroups(C.TRACK_TYPE_AUDIO)
            val dialog = AudioSelectionDialogFragment(audio)
            dialog.onAudioLanguageSelected { language ->
                val parameters = trackSelector.buildUponParameters()
                    .setPreferredAudioLanguage(language)
                    .setPreferredTextLanguage(language)
                trackSelector.setParameters(parameters)
            }
            dialog.show(activity.supportFragmentManager, null)
        }
    }

    override fun onPlayerErrorChanged(eventTime: EventTime, error: PlaybackException?) {
        super.onPlayerErrorChanged(eventTime, error)

        val channelActivity = activity as? ChannelPlaybackActivity
        channelActivity?.playerError()
    }

    override fun onCues(eventTime: EventTime, cues: MutableList<Cue>) {
        if (closedCaptionAction.index == PlaybackControlsRow.ClosedCaptioningAction.INDEX_ON) {
            closedCaptionsTextView.text = cues.joinToString { it.text ?: "" }
        }
    }


    override fun onDownstreamFormatChanged(eventTime: EventTime, mediaLoadData: MediaLoadData) {
        Log.d(TAG, "onDownstreamFormatChanged")
        val trackFormat = mediaLoadData.trackFormat
        if (mediaLoadData.dataType != C.DATA_TYPE_MEDIA || trackFormat == null) {
            return
        }
        if (mediaLoadData.trackType == C.TRACK_TYPE_DEFAULT || mediaLoadData.trackType == C.TRACK_TYPE_VIDEO) {
            currentVideoFormat = trackFormat
            updateSubtitle()
        }
    }

    private fun updateSubtitle() {
        val videoQuality = currentVideoFormat?.let { context.getString(R.string.video_quality, it.height, it.frameRate.roundToInt()) }
        val audioLanguage = currentAudioFormat?.label
        subtitle = listOfNotNull(videoQuality, audioLanguage).joinToString(separator = " / ")
    }

    private fun PlayerAdapter.seekOffset(offset: Long) {
        val position = max(min(currentPosition + offset, duration), 0)
        seekTo(position)
    }
}
