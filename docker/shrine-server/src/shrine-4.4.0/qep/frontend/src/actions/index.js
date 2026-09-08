import { createAction, createNamedArgsAction } from "../utilities";

// -- toggle views -- //
export const UPDATE_VIEW_MODE = "UPDATE_VIEW_MODE";
export const updateViewMode = createAction(UPDATE_VIEW_MODE);

// -- fetch all queries -- //
export const SORT_ALL_QUERIES = "SORT_ALL_QUERIES";
export const sortAllQueries = createAction(SORT_ALL_QUERIES);
export const FETCH_ALL_QUERIES = "FETCH_ALL_QUERIES";
export const fetchAllQueries = createAction(FETCH_ALL_QUERIES);
export const FETCH_ALL_QUERIES_FAILED = "FETCH_ALL_QUERIES_FAILED";
export const fetchAllQueriesFailed = createAction(FETCH_ALL_QUERIES_FAILED);
export const FETCH_ALL_QUERIES_SUCCEEDED = "FETCH_ALL_QUERIES_SUCCEEDED";
export const fetchAllQueriesSucceeded = createAction(
  FETCH_ALL_QUERIES_SUCCEEDED
);
export const FETCH_QUERY_SNAPSHOT = "FETCH_QUERY_SNAPSHOT";
export const fetchQuerySnapshot = createAction(FETCH_QUERY_SNAPSHOT);

// -- fetch query update -- //
export const FETCH_QUERY_UPDATE = "FETCH_QUERY_UPDATE";
export const fetchQueryUpdate = createAction(FETCH_QUERY_UPDATE);
export const FETCH_QUERY_UPDATE_FAILED = "FETCH_QUERY_UPDATE_FAILED";
export const fetchQueryUpdateFailed = createAction(FETCH_QUERY_UPDATE_FAILED);
export const FETCH_QUERY_UPDATE_SUCCEEDED = "FETCH_QUERY_UPDATE_SUCCEEDED";
export const fetchQueryUpdateSucceeded = createAction(
  FETCH_QUERY_UPDATE_SUCCEEDED
);
export const FETCH_QUERY_TIMEOUT = "FETCH_QUERY_TIMEOUT";
export const fetchQueryTimeout = createAction(FETCH_QUERY_TIMEOUT);

// -- Set Query Institution Result Sort -- //
export const ADD_SITE_SORT = "ADD_SITE_SORT";
export const addSiteSort = createNamedArgsAction(ADD_SITE_SORT, "sortSiteBy");

// query polling //
export const START_QUERY_POLL = "START_QUERY_POLL";
export const startQueryPoll = createAction(START_QUERY_POLL);
export const CONTINUE_QUERY_POLL = "CONTINUE_QUERY_POLL";
export const continueQueryPoll = createAction(CONTINUE_QUERY_POLL);
export const CANCEL_QUERY_POLL = "CANCEL_QUERY_POLL";
export const cancelQueryPoll = createAction(CANCEL_QUERY_POLL);

// -- CSV export -- //
export const EXPORT_DEMOGRAPHIC_TO_CSV = "EXPORT_DEMOGRAPHIC_TO_CSV";
export const exportDemographicToCSV = createAction(EXPORT_DEMOGRAPHIC_TO_CSV);
export const FETCH_DEMOGRAPHIC_DATA_FAILED = "FETCH_DEMOGRAPHIC_DATA_FAILED";
export const fetchDemographicDataFailed = createAction(
  FETCH_DEMOGRAPHIC_DATA_FAILED
);
export const FETCH_DEMOGRAPHIC_DATA_SUCCEEDED =
  "FETCH_DEMOGRAPHIC_DATA_SUCCEEDED";
export const fetchDemographicDataSucceeded = createAction(
  FETCH_DEMOGRAPHIC_DATA_SUCCEEDED
);

export const EXPORT_COUNT_TO_CSV = "EXPORT_COUNT_TO_CSV";
export const exportCountToCSV = createAction(EXPORT_COUNT_TO_CSV);
export const FETCH_COUNT_CSV_DATA_FAILED = "FETCH_COUNT_CSV_DATA_FAILED";
export const fetchCountCsvDataFailed = createAction(
  FETCH_COUNT_CSV_DATA_FAILED
);
export const FETCH_COUNT_CSV_DATA_SUCCEEDED = "FETCH_COUNT_CSV_DATA_SUCCEEDED";
export const fetchCountCsvDataSucceeded = createAction(
  FETCH_COUNT_CSV_DATA_SUCCEEDED
);

// -- Update Query Definition -- //
export const REMOVE_QUERY_GROUP = "REMOVE_QUERY_GROUP"; // TODO: remove this action? b/c unused and no corresponding action handler?
export const UPDATE_QUERY_GROUP_OPTIONS = "UPDATE_QUERY_GROUP_OPTIONS";
export const UPDATE_TIMELINE_EVENT_OPTIONS = "UPDATE_TIMELINE_EVENT_OPTIONS";
export const UPDATE_QUERY_GROUP_CONCEPTS = "UPDATE_QUERY_GROUP_CONCEPTS";
export const UPDATE_TIMELINE_EVENT_CONCEPTS = "UPDATE_TIMELINE_EVENT_CONCEPTS";
export const ADD_GROUP_TERM = "ADD_GROUP_TERM";
export const UPDATE_GROUP_TERM_OPTIONS = "UPDATE_GROUP_TERM_OPTIONS";
export const UPDATE_GROUP_TIMELINE_TERM = "UPDATE_GROUP_TIMELINE_TERM";
export const REMOVE_GROUP_TERM = "REMOVE_GROUP_TERM";
export const REMOVE_ALL_GROUP_TERMS = "REMOVE_ALL_GROUP_TERMS";
export const RESET_QUERY_DEFINITION = "RESET_QUERY_DEFINITION";
export const UPDATE_QUERY_GROUP_STATUS = "UPDATE_QUERY_GROUP_STATUS";
export const UPDATE_QUERY_GROUP_TIMELINE_LINK =
  "UPDATE_QUERY_GROUP_TIMELINE_LINK";

export const updateQueryGroupOptions = createNamedArgsAction(
  UPDATE_QUERY_GROUP_OPTIONS,
  "id",
  "newOptions"
);
export const updateTimelineEventOptions = createAction(
  UPDATE_TIMELINE_EVENT_OPTIONS
);
export const updateQueryGroupConcepts = createAction(
  UPDATE_QUERY_GROUP_CONCEPTS
);
export const updateTimelineEventConcepts = createAction(
  UPDATE_TIMELINE_EVENT_CONCEPTS
);
export const addGroupTerm = createNamedArgsAction(ADD_GROUP_TERM, "id", "term");
export const updateGroupTermOptionsOptions = createNamedArgsAction(
  UPDATE_GROUP_TERM_OPTIONS,
  "id",
  "termPath",
  "newOptions"
);
export const updateGroupTimelineTerm = createAction(UPDATE_GROUP_TIMELINE_TERM);
export const removeGroupTerm = createNamedArgsAction(
  REMOVE_GROUP_TERM,
  "id",
  "termPath"
);
export const removeAllGroupTerms = createNamedArgsAction(
  REMOVE_ALL_GROUP_TERMS,
  "groupId"
);
export const resetQueryDefinition = createNamedArgsAction(
  RESET_QUERY_DEFINITION
);
export const updateQueryGroupStatus = createAction(UPDATE_QUERY_GROUP_STATUS);

export const updateQueryGroupTimelineLink = createAction(
  UPDATE_QUERY_GROUP_TIMELINE_LINK
);

// -- Ontology terms -- //
export const FETCH_ONTOLOGY_ROOT = "FETCH_ONTOLOGY_ROOT";
export const fetchOntologyRoot = createAction(FETCH_ONTOLOGY_ROOT);
export const FETCH_ONTOLOGY_ROOT_FAILED = "FETCH_ONTOLOGY_ROOT_FAILED";
export const fetchOntologyRootFailed = createAction(FETCH_ONTOLOGY_ROOT_FAILED);
export const FETCH_ONTOLOGY_ROOT_SUCCEEDED = "FETCH_ONTOLOGY_ROOT_SUCCEEDED";
export const fetchOntologyRootSucceeded = createAction(
  FETCH_ONTOLOGY_ROOT_SUCCEEDED
);
export const FETCH_ONTOLOGY_CHILDREN = "FETCH_ONTOLOGY_CHILDREN";
export const fetchOntologyChildren = createAction(FETCH_ONTOLOGY_CHILDREN);
export const FETCH_ONTOLOGY_CHILDREN_FAILED = "FETCH_ONTOLOGY_CHILDREN_FAILED";
export const fetchOntologyChildrenFailed = createAction(
  FETCH_ONTOLOGY_CHILDREN_FAILED
);
export const FETCH_ONTOLOGY_CHILDREN_SUCCEEDED =
  "FETCH_ONTOLOGY_CHILDREN_SUCCEEDED";
export const fetchOntologyChildrenSucceeded = createAction(
  FETCH_ONTOLOGY_CHILDREN_SUCCEEDED
);

// -- ontology filter -- //
export const FETCH_FILTERED_ONTOLOGY = "FETCH_FILTERED_ONTOLOGY";
export const fetchFilteredOntology = createAction(FETCH_FILTERED_ONTOLOGY);
export const FETCH_FILTERED_ONTOLOGY_FAILED = "FETCH_FILTERED_ONTOLOGY_FAILED";
export const fetchFilteredOntologyFailed = createAction(
  FETCH_FILTERED_ONTOLOGY_FAILED
);
export const FETCH_FILTERED_ONTOLOGY_SUCCEEDED =
  "FETCH_FILTERED_ONTOLOGY_SUCCEEDED";
export const fetchFilteredOntologySucceeded = createAction(
  FETCH_FILTERED_ONTOLOGY_SUCCEEDED
);
export const REMOVE_ONTOLOGY_FILTER = "REMOVE_ONTOLOGY_FILTER";
export const removeOntologyFilter = createAction(REMOVE_ONTOLOGY_FILTER);

// -- ontology filter options -- //
export const FETCH_FILTER_OPTIONS = "FETCH_FILTER_OPTIONS";
export const fetchFilterOptions = createAction(FETCH_FILTER_OPTIONS);
export const FETCH_FILTER_OPTIONS_FAILED = "FETCH_FILTER_OPTIONS_FAILED";
export const fetchFilterOptionsFailed = createAction(
  FETCH_FILTER_OPTIONS_FAILED
);
export const FETCH_FILTER_OPTIONS_SUCCEEDED = "FETCH_FILTER_OPTIONS_SUCCEEDED";
export const fetchFilterOptionsSucceeded = createAction(
  FETCH_FILTER_OPTIONS_SUCCEEDED
);

// ontology autosuggest.
export const FETCH_ONTOLOGY_SEARCH_SUGGESTIONS =
  "FETCH_ONTOLOGY_SEARCH_SUGGESTIONS";
export const fetchOntologySearchSuggestions = createAction(
  FETCH_ONTOLOGY_SEARCH_SUGGESTIONS
);
export const FETCH_ONTOLOGY_SEARCH_SUGGESTIONS_FAILED =
  "FETCH_ONTOLOGY_SEARCH_SUGGESTIONS_FAILED";
export const fetchOntologySearchSuggestionsFailed = createAction(
  FETCH_ONTOLOGY_SEARCH_SUGGESTIONS_FAILED
);
export const FETCH_ONTOLOGY_SEARCH_SUGGESTIONS_SUCCEEDED =
  "FETCH_ONTOLOGY_SEARCH_SUGGESTIONS_SUCCEEDED";
export const fetchOntologySearchSuggestionsSucceeded = createAction(
  FETCH_ONTOLOGY_SEARCH_SUGGESTIONS_SUCCEEDED
);

// -- start query -- //
export const START_QUERY = "START_QUERY";
export const startQuery = createAction(START_QUERY);
export const START_QUERY_SUCCEEDED = "START_QUERY_SUCCEEDED";
export const startQuerySucceeded = createAction(START_QUERY_SUCCEEDED);
export const START_QUERY_FAILED = "START_QUERY_FAILED";
export const startQueryFailed = createAction(START_QUERY_FAILED);

// -- login -- //
export const LOGIN_USER = "LOGIN_USER";
export const loginUser = createAction(LOGIN_USER);
export const LOGIN_USER_SUCCEEDED = "LOGIN_USER_SUCCEEDED";
export const loginUserSucceeded = createAction(LOGIN_USER_SUCCEEDED);
export const LOGIN_USER_FAILED = "LOGIN_USER_FAILED";
export const loginUserFailed = createAction(LOGIN_USER_FAILED);
export const CLEAR_LOGIN = "CLEAR_LOGIN";
export const clearLogin = createAction(CLEAR_LOGIN);
export const CLEAR_WARNING = "CLEAR_WARNING";
export const clearWarning = createAction(CLEAR_WARNING);

// -- user -- //
export const SET_USER_LOCAL_CONFIG = "SET_USER_LOCAL_CONFIG";
export const setUserLocalConfig = createAction(SET_USER_LOCAL_CONFIG);

export const ACCEPT_TERMS_OF_USE = "ACCEPT_TERMS_OF_USE";
export const acceptTermsOfUse = createAction(ACCEPT_TERMS_OF_USE);

// -- network config -- //
export const FETCH_NETWORK_CONFIG = "FETCH_NETWORK_CONFIG";
export const fetchNetworkConfig = createAction(FETCH_NETWORK_CONFIG);
export const FETCH_NETWORK_CONFIG_FAILED = "FETCH_NETWORK_CONFIG_FAILED";
export const fetchNetworkConfigFailed = createAction(
  FETCH_NETWORK_CONFIG_FAILED
);
export const FETCH_NETWORK_CONFIG_SUCCEEDED = "FETCH_NETWORK_CONFIG_SUCCEEDED";
export const fetchNetworkConfigSucceeded = createAction(
  FETCH_NETWORK_CONFIG_SUCCEEDED
);

// -- extend user session --
export const KEEP_ALIVE = "KEEP_ALIVE";
export const keepAlive = createNamedArgsAction(KEEP_ALIVE);
export const KEEP_ALIVE_STARTED = "KEEP_ALIVE_STARTED";
export const keepAliveStarted = createNamedArgsAction(KEEP_ALIVE_STARTED);
export const KEEP_ALIVE_SUCCEEDED = "KEEP_ALIVE_SUCCEEDED";
export const keepAliveSucceeded = createNamedArgsAction(KEEP_ALIVE_SUCCEEDED);
export const KEEP_ALIVE_FAILED = "KEEP_ALIVE_FAILED";
export const keepAliveFailed = createNamedArgsAction(KEEP_ALIVE_FAILED);

// -- reloading a query -- //
export const RELOAD_QUERY = "RELOAD_QUERY";
export const reloadQuery = createNamedArgsAction(RELOAD_QUERY, "queryId");
export const RELOAD_QUERY_SUCCEEDED = "RELOAD_QUERY_SUCCEEDED";
export const reloadQuerySucceeded = createAction(RELOAD_QUERY_SUCCEEDED);
export const RELOAD_QUERY_FAILED = "RELOAD_QUERY_FAILED";
export const reloadQueryFailed = createAction(RELOAD_QUERY_FAILED);

// -- query result options -- //
// TODO-XH: use the same argument name for the query ID in the following actions
export const FAV_QUERY = "FAV_QUERY";
export const favQuery = createNamedArgsAction(
  FAV_QUERY,
  "queryId",
  "faved"
);
export const RENAME_QUERY = "RENAME_QUERY";
export const renameQuery = createNamedArgsAction(
  RENAME_QUERY,
  "queryId",
  "name",
  "notes"
);

// Unexpected Error
export const UNEXPECTED_ERROR = "UNEXPECTED_ERROR";
export const unexpectedError = createAction(UNEXPECTED_ERROR);

//Data Distribution Types
export const FETCH_DATA_DISTRIBUTION_TYPES = "FETCH_DATA_DISTRIBUTION_TYPES";
export const fetchDataDistributionTypes = createAction(FETCH_DATA_DISTRIBUTION_TYPES);
export const FETCH_DATA_DISTRIBUTION_TYPES_SUCCEEDED = "FETCH_DATA_DISTRIBUTION_TYPES_SUCCEEDED";
export const fetchDataDistributionTypesSucceeded = createAction(FETCH_DATA_DISTRIBUTION_TYPES_SUCCEEDED);
export const FETCH_DATA_DISTRIBUTION_TYPES_FAILED = "FETCH_DATA_DISTRIBUTION_TYPES_FAILED";
export const fetchDataDistributionTypesFailed = createAction(FETCH_DATA_DISTRIBUTION_TYPES_FAILED);

