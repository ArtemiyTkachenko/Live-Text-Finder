package com.artkachenko.livetextfinder.status

import androidx.compose.runtime.Composable
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.search_results_guidance_area_mapping
import livetextfinder.shared.generated.resources.search_results_guidance_error
import livetextfinder.shared.generated.resources.search_results_guidance_freezing
import livetextfinder.shared.generated.resources.search_results_guidance_hidden
import livetextfinder.shared.generated.resources.search_results_guidance_importing
import livetextfinder.shared.generated.resources.search_results_guidance_missing_frozen
import livetextfinder.shared.generated.resources.search_results_guidance_missing_imported
import livetextfinder.shared.generated.resources.search_results_guidance_paused
import livetextfinder.shared.generated.resources.search_results_guidance_permission
import livetextfinder.shared.generated.resources.search_results_guidance_starting
import livetextfinder.shared.generated.resources.search_results_guidance_unavailable
import livetextfinder.shared.generated.resources.search_results_guidance_waiting
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun unavailableResultsExplanation(guidance: UnavailableResultsGuidance?): String? =
    when (guidance) {
        UnavailableResultsGuidance.Importing ->
            stringResource(Res.string.search_results_guidance_importing)
        UnavailableResultsGuidance.Freezing ->
            stringResource(Res.string.search_results_guidance_freezing)
        UnavailableResultsGuidance.MissingImportedSource ->
            stringResource(Res.string.search_results_guidance_missing_imported)
        UnavailableResultsGuidance.MissingFrozenSource ->
            stringResource(Res.string.search_results_guidance_missing_frozen)
        UnavailableResultsGuidance.Permission ->
            stringResource(Res.string.search_results_guidance_permission)
        UnavailableResultsGuidance.Starting ->
            stringResource(Res.string.search_results_guidance_starting)
        UnavailableResultsGuidance.Paused ->
            stringResource(Res.string.search_results_guidance_paused)
        UnavailableResultsGuidance.Error ->
            stringResource(Res.string.search_results_guidance_error)
        UnavailableResultsGuidance.Unavailable ->
            stringResource(Res.string.search_results_guidance_unavailable)
        UnavailableResultsGuidance.HiddenPreview ->
            stringResource(Res.string.search_results_guidance_hidden)
        UnavailableResultsGuidance.AreaMapping ->
            stringResource(Res.string.search_results_guidance_area_mapping)
        UnavailableResultsGuidance.Waiting ->
            stringResource(Res.string.search_results_guidance_waiting)
        null -> null
    }
