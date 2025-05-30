package fr.free.nrw.commons.description

import android.app.ProgressDialog
import android.os.Bundle
import android.os.Parcelable
import android.speech.RecognizerIntent
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import fr.free.nrw.commons.CommonsApplication
import fr.free.nrw.commons.Media
import fr.free.nrw.commons.R
import fr.free.nrw.commons.auth.SessionManager
import fr.free.nrw.commons.auth.csrf.InvalidLoginTokenException
import fr.free.nrw.commons.databinding.ActivityDescriptionEditBinding
import fr.free.nrw.commons.description.EditDescriptionConstants.LIST_OF_DESCRIPTION_AND_CAPTION
import fr.free.nrw.commons.description.EditDescriptionConstants.WIKITEXT
import fr.free.nrw.commons.recentlanguages.RecentLanguagesDao
import fr.free.nrw.commons.settings.Prefs
import fr.free.nrw.commons.theme.BaseActivity
import fr.free.nrw.commons.upload.UploadMediaDetail
import fr.free.nrw.commons.upload.UploadMediaDetailAdapter
import fr.free.nrw.commons.utils.DialogUtil.showAlertDialog
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject

/**
 * Activity for populating and editing existing description and caption
 */
class DescriptionEditActivity :
    BaseActivity(),
    UploadMediaDetailAdapter.EventListener {
    /**
     * Adapter for showing UploadMediaDetail in the activity
     */
    private lateinit var uploadMediaDetailAdapter: UploadMediaDetailAdapter

    /**
     * Recyclerview for recycling data in views
     */
    @JvmField
    var rvDescriptions: RecyclerView? = null

    /**
     * Current wikitext
     */
    var wikiText: String? = null

    /**
     * Media object
     */
    var media: Media? = null

    /**
     * Saved language
     */
    private lateinit var savedLanguageValue: String

    /**
     * For showing progress dialog
     */
    private var progressDialog: ProgressDialog? = null

    @Inject
    lateinit var recentLanguagesDao: RecentLanguagesDao

    private lateinit var binding: ActivityDescriptionEditBinding

    private var descriptionAndCaptions: MutableList<UploadMediaDetail>? = null

    private val voiceInputResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        onVoiceInput(result)
    }

    @Inject lateinit var descriptionEditHelper: DescriptionEditHelper

    @Inject lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDescriptionEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bundle = intent.extras

        if (savedInstanceState != null) {
            descriptionAndCaptions = savedInstanceState.getParcelableArrayList(LIST_OF_DESCRIPTION_AND_CAPTION)
            wikiText = savedInstanceState.getString(WIKITEXT)
            savedLanguageValue = savedInstanceState.getString(Prefs.DESCRIPTION_LANGUAGE)!!
            media = savedInstanceState.getParcelable("media")
        } else {
            descriptionAndCaptions =
                bundle!!.getParcelableArrayList(LIST_OF_DESCRIPTION_AND_CAPTION)!!
            wikiText = bundle.getString(WIKITEXT)
            savedLanguageValue = bundle.getString(Prefs.DESCRIPTION_LANGUAGE)!!
            media = bundle.getParcelable("media")
        }
        initRecyclerView(descriptionAndCaptions)

        binding.btnEditSubmit.setOnClickListener(::onSubmitButtonClicked)
        binding.toolbarBackButton.setOnClickListener(::onBackButtonClicked)
    }

    /**
     * Initializes the RecyclerView
     * @param descriptionAndCaptions list of description and caption
     */
    private fun initRecyclerView(descriptionAndCaptions: MutableList<UploadMediaDetail>?) {
        uploadMediaDetailAdapter =
            UploadMediaDetailAdapter(
                this,
                savedLanguageValue,
                descriptionAndCaptions ?: mutableListOf(),
                recentLanguagesDao,
                voiceInputResultLauncher
            )

        uploadMediaDetailAdapter.callback = UploadMediaDetailAdapter.Callback(::showInfoAlert)
        uploadMediaDetailAdapter.eventListener = this
        rvDescriptions = binding.rvDescriptionsCaptions
        rvDescriptions!!.layoutManager = LinearLayoutManager(this)
        rvDescriptions!!.adapter = uploadMediaDetailAdapter
    }

    /**
     * show dialog with info
     * @param titleStringID Title ID
     * @param messageStringId Message ID
     */
    private fun showInfoAlert(
        titleStringID: Int,
        messageStringId: Int,
    ) {
        showAlertDialog(
            this,
            getString(titleStringID),
            getString(messageStringId),
            getString(android.R.string.ok),
            null
        )
    }

    override fun onPrimaryCaptionTextChange(isNotEmpty: Boolean) {}

    private fun onVoiceInput(result: ActivityResult) {
        if (result.resultCode == RESULT_OK && result.data != null) {
            val resultData = result.data!!.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            uploadMediaDetailAdapter.handleSpeechResult(resultData!![0])
        } else {
            Timber.e("Error %s", result.resultCode)
        }
    }

    /**
     * Adds new language item to RecyclerView
     */
    override fun addLanguage() {
        val uploadMediaDetail = UploadMediaDetail()
        uploadMediaDetail.isManuallyAdded = true // This was manually added by the user
        uploadMediaDetailAdapter.addDescription(uploadMediaDetail)
        rvDescriptions!!.smoothScrollToPosition(uploadMediaDetailAdapter.itemCount - 1)
    }

    private fun onBackButtonClicked(view: View) {
        onBackPressedDispatcher.onBackPressed()
    }

    private fun onSubmitButtonClicked(view: View) {
        showLoggingProgressBar()
        val uploadMediaDetails = uploadMediaDetailAdapter.items
        updateDescription(uploadMediaDetails)
        finish()
    }

    /**
     * Updates newly added descriptions in the wikiText and send to calling fragment
     * @param uploadMediaDetails descriptions and captions
     */
    private fun updateDescription(uploadMediaDetails: List<UploadMediaDetail?>) {
        var descriptionIndex = wikiText!!.indexOf("description=")
        if (descriptionIndex == -1) {
            descriptionIndex = wikiText!!.indexOf("Description=")
        }
        val buffer = StringBuilder()
        if (descriptionIndex != -1) {
            val descriptionStart = wikiText!!.substring(0, descriptionIndex + 12)
            val descriptionToEnd = wikiText!!.substring(descriptionIndex + 12)
            val descriptionEndIndex = descriptionToEnd.indexOf("\n")
            val descriptionEnd =
                wikiText!!.substring(
                    descriptionStart.length +
                        descriptionEndIndex,
                )
            buffer.append(descriptionStart)
            for (i in uploadMediaDetails.indices) {
                val uploadDetails = uploadMediaDetails[i]
                if (uploadDetails!!.descriptionText != "") {
                    buffer.append("{{")
                    buffer.append(uploadDetails.languageCode)
                    buffer.append("|1=")
                    buffer.append(uploadDetails.descriptionText)
                    buffer.append("}}")
                }
            }
            buffer.replace(", $".toRegex(), "")
            buffer.append(descriptionEnd)
        }
        editDescription(media!!, buffer.toString(), uploadMediaDetails as ArrayList<UploadMediaDetail>)

        finish()
    }

    /**
     * Edits description and caption
     * @param media media object
     * @param updatedWikiText updated wiki text
     * @param uploadMediaDetails descriptions and captions
     */
    private fun editDescription(
        media: Media,
        updatedWikiText: String,
        uploadMediaDetails: ArrayList<UploadMediaDetail>,
    ) {
        try {
            descriptionEditHelper
                .addDescription(
                    applicationContext,
                    media,
                    updatedWikiText,
                ).subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(Consumer<Boolean> { s: Boolean? -> Timber.d("Descriptions are added.") })
                ?.let {
                    compositeDisposable.add(
                        it,
                    )
                }
        } catch (e: InvalidLoginTokenException) {
            val username: String? = sessionManager.userName
            val logoutListener =
                CommonsApplication.BaseLogoutListener(
                    this,
                    getString(R.string.invalid_login_message),
                    username,
                )

            val commonsApplication = CommonsApplication.instance
            if (commonsApplication != null) {
                commonsApplication.clearApplicationData(this, logoutListener)
            }
        }

        val updatedCaptions = LinkedHashMap<String, String>()
        for (mediaDetail in uploadMediaDetails) {
            try {
                compositeDisposable.add(
                    descriptionEditHelper
                        .addCaption(
                            applicationContext,
                            media,
                            mediaDetail.languageCode!!,
                            mediaDetail.captionText,
                        ).subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe { s: Boolean? ->
                            updatedCaptions[mediaDetail.languageCode!!] = mediaDetail.captionText
                            media.captions = updatedCaptions
                            Timber.d("Caption is added.")
                        },
                )
            } catch (e: InvalidLoginTokenException) {
                val username = sessionManager.userName
                val logoutListener =
                    CommonsApplication.BaseLogoutListener(
                        this,
                        getString(R.string.invalid_login_message),
                        username,
                    )

                val commonsApplication = CommonsApplication.instance
                if (commonsApplication != null) {
                    commonsApplication.clearApplicationData(this, logoutListener)
                }
            }
        }
    }

    private fun showLoggingProgressBar() {
        progressDialog = ProgressDialog(this)
        progressDialog!!.isIndeterminate = true
        progressDialog!!.setTitle(getString(R.string.updating_caption_title))
        progressDialog!!.setMessage(getString(R.string.updating_caption_message))
        progressDialog!!.setCancelable(false)
        progressDialog!!.show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putParcelableArrayList(LIST_OF_DESCRIPTION_AND_CAPTION, uploadMediaDetailAdapter.items as ArrayList<out Parcelable?>)
        outState.putString(WIKITEXT, wikiText)
        outState.putString(Prefs.DESCRIPTION_LANGUAGE, savedLanguageValue)
        // save Media
        outState.putParcelable("media", media)
    }
}
