package fr.free.nrw.commons.nearby.fragments

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.LifecycleCoroutineScope
import fr.free.nrw.commons.bookmarks.locations.BookmarkLocationsDao
import fr.free.nrw.commons.nearby.Place
import fr.free.nrw.commons.nearby.placeAdapterDelegate
import fr.free.nrw.commons.upload.categories.BaseDelegateAdapter

class PlaceAdapter(
    bookmarkLocationsDao: BookmarkLocationsDao,
    scope: LifecycleCoroutineScope? = null,
    onPlaceClicked: ((Place) -> Unit)? = null,
    onBookmarkClicked: (Place, Boolean) -> Unit,
    commonPlaceClickActions: CommonPlaceClickActions,
    inAppCameraLocationPermissionLauncher: ActivityResultLauncher<Array<String>>,
    galleryPickLauncherForResult: ActivityResultLauncher<Intent>,
    cameraPickLauncherForResult: ActivityResultLauncher<Intent>
) : BaseDelegateAdapter<Place>(
        placeAdapterDelegate(
            bookmarkLocationsDao,
            scope,
            onPlaceClicked,
            commonPlaceClickActions.onCameraClicked(),
            commonPlaceClickActions.onCameraLongPressed(),
            commonPlaceClickActions.onGalleryClicked(),
            commonPlaceClickActions.onGalleryLongPressed(),
            onBookmarkClicked,
            commonPlaceClickActions.onBookmarkLongPressed(),
            commonPlaceClickActions.onOverflowClicked(),
            commonPlaceClickActions.onOverflowLongPressed(),
            commonPlaceClickActions.onDirectionsClicked(),
            commonPlaceClickActions.onDirectionsLongPressed(),
            inAppCameraLocationPermissionLauncher,
            cameraPickLauncherForResult,
            galleryPickLauncherForResult
        ),
        areItemsTheSame = { oldItem, newItem -> oldItem.wikiDataEntityId == newItem.wikiDataEntityId },
    )
