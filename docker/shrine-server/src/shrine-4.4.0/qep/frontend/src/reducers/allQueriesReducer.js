import { Error, Fav, QueryResult } from "models";
import { defaultState } from "defaultState";
import { clearOnLogout } from "utilities";
import {
  SORT_ALL_QUERIES,
  FETCH_ALL_QUERIES,
  FETCH_ALL_QUERIES_FAILED,
  FETCH_ALL_QUERIES_SUCCEEDED,
  FAV_QUERY,
  LOGIN_USER,
} from "actions";

export const allQueriesReducer = (state = defaultState.allQueries, action) => {
  switch (action.type) {
    case SORT_ALL_QUERIES: {
      const { sortBy = state.sortBy } = action.payload;

      return {
        ...state,
        sortBy,
        skip: 0,
        isFetching: true
      };
    }
    case FETCH_ALL_QUERIES: {
      const { skip } = action.payload;

      return {
        ...state,
        skip,
        isFetching: true
      };
    }
    case FETCH_ALL_QUERIES_FAILED: {
      const { status, statusText, url } = action.payload;
      const error = Error({
        hasError: true,
        message: `${status} ${statusText}`,
        url
      });

      return {
        ...state,
        isFetching: false,
        error
      };
    }

    case FETCH_ALL_QUERIES_SUCCEEDED: {
      console.log("state: ", state)
      console.log("action.payload: ", action.payload)
      const { allQueries, rowCount } = action.payload;

      const isFetching = false;
      const results = allQueries.map((r) => {
        return QueryResult({
          ...r,
          queryId:
            r.networkId /*
          todo:  when server side work is done for SHRINE2020-290
          the above line will be unnecessary...r can just be spread.
        */
        });
      });
      return {
        ...state,
        results,
        rowCount,
        isFetching
      };
    }

    case FAV_QUERY: {
      const { queryId, queryFaved } = action.payload;
      const favBody = Fav({
        queryFaved
      });
      return {
        ...state,
        results: state.results.map((queryResult) => {
          if (queryResult.queryId === queryId) {
            return {
              ...queryResult,
              queryFaved: favBody
            };
          }
          return queryResult;
        })
      };
    }

    case LOGIN_USER: {
      /* Remove any existing query results on user login */
      return defaultState.allQueries
    }

    default: {
      return clearOnLogout(action.type, defaultState.allQueries, state);
    }
  }
};
