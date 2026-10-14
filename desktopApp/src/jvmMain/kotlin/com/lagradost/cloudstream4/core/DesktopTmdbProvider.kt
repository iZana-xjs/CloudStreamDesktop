package com.lagradost.cloudstream4.core

import com.lagradost.cloudstream3.metaproviders.TmdbProvider

/** TMDB is used as a metadata catalog on desktop, so detail pages must return TMDB data. */
class DesktopTmdbProvider : TmdbProvider() {
    override var name: String = "TMDB"
    override val useMetaLoadResponse: Boolean = true
}
