import { call, takeLatest, put } from "redux-saga/effects";
import { ViewModeTypes } from "models";

import {
  START_QUERY,
  startQuerySucceeded,
  startQueryFailed,
  updateViewMode,
  startQueryPoll,
} from "../actions";
import {getBaseUrl, secureFetchOrLogout} from "../utilities";

const normalizeConstraint = (constraint) => {
  const { constraintType, value, unit } = constraint;
  if (constraintType === "ANY") return null;
  return {
    constraintType,
    value: value
      .filter((v) => v !== null && v !== undefined)
      .map((v) => v.toString()),
    unit,
  };
};

const formatConceptListContainer = (conceptListContainer) => {
  const concepts = conceptListContainer.concepts.map((concept) => ({
    displayName: concept.displayName,
    path: concept.path,
    constraint: normalizeConstraint(concept.constraint),
  }));
  const {
    isExcluded = false,
    options = null,
  } = conceptListContainer;

  return Object.fromEntries(
    Object.entries({ isExcluded, options, concepts }).filter(
      ([, value]) => value !== null
    )
  );
};

const formatRequestBody = (queryDefinition) => {
  // queryNotes, or some constant for debugging
  const {
    queryName,
    queryNotes,
    queryTermGroups,
    selectedDataDistTypes,
    timelineGroupId,
  } = queryDefinition;

  const conceptGroups = [...queryTermGroups.values()]
    .filter(
      (conceptGroup) =>
        conceptGroup.id !== timelineGroupId && conceptGroup.concepts.length > 0
    )
    .map(formatConceptListContainer);

  const timelineGroup = queryTermGroups.get(timelineGroupId);

  let timeline = null;
  if (timelineGroup) {
    const nonEmptyTimelineLinks = timelineGroup.timeline.timelineLinks.filter((timelineLink, index) =>
      timelineGroup.timeline.timelineEvents[index+1].concepts.length !== 0);

    const nonEmptyTimelineEvents = timelineGroup.timeline.timelineEvents.filter(timelineEvent => timelineEvent.concepts.length > 0).map(formatConceptListContainer);

    timeline = {
      ...timelineGroup.timeline,
      timelineEvents: nonEmptyTimelineEvents,
      timelineLinks: nonEmptyTimelineLinks
    };
  }

  return {
    name: queryName,
    notes: queryNotes,
    faved: false,
    dataDistributionTypes: selectedDataDistTypes,
    conceptGroups,
    timeline,
  };
};

export function* doStartQuery(action) {
  const requestBody = formatRequestBody(action.payload);

  try {
    const startQueryUrl = `${getBaseUrl()}qep/startQuery`;
    const fetchConfig = {
      headers: { "Content-Type": "application/json" },
      method: "POST",
      body: JSON.stringify(requestBody),
    };

    const response = yield call(secureFetchOrLogout, startQueryUrl, fetchConfig);
    if (response.ok === true) {
      const queryRunStatus = response.data;
      /*
        todo:  when server side work is done for SHRINE2020-290
        the following line should read:
        const { queryId } = queryRunStatus;
       */
      const { networkQueryId: queryId } = queryRunStatus;
      yield put(startQuerySucceeded(queryId));
      yield put(updateViewMode(ViewModeTypes.QUERY_RESULTS));
      yield put(startQueryPoll({ queryId }));
    } else {
      yield put(startQueryFailed({ response }));
    }
  } finally {
    const msg = `Start query closed`;
    yield msg;
  }
}

export function* startQuerySaga() {
  yield takeLatest(START_QUERY, doStartQuery);
}
