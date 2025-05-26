package fr.groggy.racecontrol.tv.ui.session

import android.net.Uri
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.ImageCardView.CARD_TYPE_FLAG_CONTENT
import androidx.leanback.widget.ImageCardView.CARD_TYPE_FLAG_TITLE
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import fr.groggy.racecontrol.tv.R
import fr.groggy.racecontrol.tv.core.settings.SettingsRepository
import fr.groggy.racecontrol.tv.f1tv.F1TvClient
import javax.inject.Inject

class SessionCardPresenter @Inject constructor(
    private val settingsRepository: SettingsRepository
) : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val imageCardView = ImageCardView(parent.context)

        imageCardView.setMainImageDimensions(
            F1TvClient.MAIN_IMAGE_WIDTH,
            F1TvClient.MAIN_IMAGE_HEIGHT
        )
        imageCardView.cardType = CARD_TYPE_FLAG_TITLE or CARD_TYPE_FLAG_CONTENT

        // Remove this line as the resource doesn't exist in newer versions
        // imageCardView.findViewById<TextView>(androidx.leanback.R.id.lb_image_card_view_title_text)?.setLines(2)

        return ViewHolder(imageCardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val imageCardView = viewHolder.view as ImageCardView
        val session = item as SessionCard

        imageCardView.titleText = session.name
        imageCardView.contentText = session.contentSubtype

        if (settingsRepository.getCurrent().displayThumbnailsEnabled)
            Glide.with(viewHolder.view.context)
                .load(session.thumbnail?.url)
                .into(imageCardView.mainImageView!!)
        }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val imageCardView = viewHolder.view as ImageCardView
        imageCardView.badgeImage = null
        imageCardView.mainImage = null
    }

}

interface SessionCard {

    val name: String
    val contentSubtype: String
    val thumbnail: Image?

    interface Image {
        val url: Uri
    }

}
