import {
  Error,
  InstitutionResult,
  QueryResult,
  SelectedQuery,
  DemographicDistribution,
  DemographicDistributionResult,
} from "models";
import { clearOnLogout } from "utilities";
import { defaultState } from "defaultState";
import {
  ADD_SITE_SORT,
  FETCH_QUERY_UPDATE,
  FETCH_QUERY_UPDATE_FAILED,
  FETCH_QUERY_UPDATE_SUCCEEDED,
  START_QUERY_FAILED,
  START_QUERY_SUCCEEDED,
  START_QUERY_POLL,
  FETCH_DEMOGRAPHIC_DATA_SUCCEEDED,
  FETCH_COUNT_CSV_DATA_FAILED,
  FETCH_COUNT_CSV_DATA_SUCCEEDED,
  LOGIN_USER,
} from "../actions";

export const selectedQueryReducer = (
  state = defaultState.selectedQuery,
  action
) => {
  switch (action.type) {
    case START_QUERY_POLL: {
      const {sortSiteBy} = state;
      return SelectedQuery({
        sortSiteBy,
        isNewDataFetch: true,
      });
    }
    case START_QUERY_SUCCEEDED:
    case FETCH_QUERY_UPDATE: {
      const {
        queryId,
        dataVersion = defaultState.selectedQuery.dataVersion,
        queryName = state.queryResult.queryName,
        queryAsHtmlString = state.queryResult.queryAsHtmlString,
        changeDate= state.queryResult.changeDate,
        demographicDistribution = state.demographicDistribution,

      } = action.payload;
      const isFetching = true;
      const { isNewDataFetch, sortSiteBy } = state;
      const isInitialFetch = queryId !== state.queryId;
      const useState = isInitialFetch ? defaultState.selectedQuery : state;

      // This object is not wrapped by a QueryResult() model because of the difference between the
      // model arguments and the state fields.
      const queryResult = {
        ...useState.queryResult,
        queryId,
        queryName,
        queryAsHtmlString,
        changeDate,
      };

      return SelectedQuery({
        ...useState,
        queryId,
        queryResult,
        dataVersion,
        isFetching,
        sortSiteBy,
        isNewDataFetch,
        demographicDistribution,
        statusMsg: "Response received from network.  Waiting on results",
      });
    }

    case START_QUERY_FAILED:
    case FETCH_QUERY_UPDATE_FAILED:
    case FETCH_COUNT_CSV_DATA_FAILED: {
      const { response } = action.payload;
      const { status, statusText, url } = response;
      const error = Error({
        hasError: true,
        message: `${status} ${statusText}`,
        url,
      });

      return SelectedQuery({
        isFetching: false,
        error,
      });
    }

    case FETCH_QUERY_UPDATE_SUCCEEDED: {
      const {
        query,
        results,
        siteCount,
        patientCount,
        isComplete,
        dataVersion,
        aggregateDemographics,
      } = action.payload.selectedQuery;

      const isFetching = false;
      const institutionResults = results.map((r) => InstitutionResult(r));

      const parseDescription = (location, graphData) =>
        location
          .split(".")
          .reduce(
            (result, currentElement) =>
              result[currentElement] ? result[currentElement] : "Unavailable",
            graphData
          );

      const demographicDistribution = aggregateDemographics.map(
        (demographic) => {
          const description = parseDescription(
            "resultType.i2b2Options.description",
            demographic
          );
          const demographicDistributionResults = demographic.results.map(
            (result) => DemographicDistributionResult(result)
          );
          return DemographicDistribution({
            description,
            results: demographicDistributionResults,
          });
        }
      );

      return SelectedQuery({
        ...state,
        queryId: query.networkId,
        queryResult: QueryResult(query),
        institutionResults,
        demographicDistribution,
        siteCount,
        patientCount,
        isComplete,
        dataVersion,
        isFetching,
        isNewDataFetch: false,
      });
    }

    case ADD_SITE_SORT: {
      const { sortSiteBy } = action.payload;
      return {
        ...state,
        sortSiteBy,
      };
    }

    case FETCH_DEMOGRAPHIC_DATA_SUCCEEDED: {
      const csvDownloadURL = action.payload;
      return {
        ...state,
        csvDownloadURL: csvDownloadURL,
      };
    }

    case FETCH_COUNT_CSV_DATA_SUCCEEDED: {
      const countCsvDownloadURL = action.payload;
      return {
        ...state,
        countCsvDownloadURL: countCsvDownloadURL,
      };
    }

    case LOGIN_USER: {
      /* Remove any existing query results on user login */
      return defaultState.selectedQuery
    }

    default: {
      return clearOnLogout(action.type, defaultState.selectedQuery, state);
    }
  }
};
