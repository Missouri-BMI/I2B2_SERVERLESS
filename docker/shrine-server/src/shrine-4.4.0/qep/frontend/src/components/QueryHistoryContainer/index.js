import React, { useEffect, useState } from "react";
import { connect } from "react-redux";
import PropTypes from "prop-types";

import {
  sortAllQueries,
  fetchAllQueries,
  startQueryPoll,
  favQuery,
  renameQuery,
} from "actions";
import { AllQueries } from "models";
import { FetchError } from "../FetchError";
import { QueryHistory } from "./QueryHistory";
import { QueryHistoryContext } from "./QueryHistoryContext";

export const WrappedQueryHistoryContainer = ({
  allQueries,
  dispatch,
  selectedQueryId,
  favTexts,
}) => {
  const [sortBy, setSortBy] = useState(allQueries.sortBy);
  const [skip, setSkip] = useState(allQueries.skip);
  // -- prevent extra sorting/query history calls when component is mounting. -- //
  const [readyToSort, setReadyToSort] = useState(false);
  const lastSkipIndex = allQueries.rowCount - allQueries.limit;

  const updateSort = (newSortBy) => {
    if (newSortBy !== sortBy) {
      setSortBy(newSortBy);
    }
  };

  const pageForward = () => {
    if (skip < lastSkipIndex) {
      const isLastPage = skip + allQueries.limit >= lastSkipIndex;
      setSkip(isLastPage ? lastSkipIndex : skip + allQueries.limit);
    }
  };

  const pageBack = () => {
    if (skip > 0) {
      const isFirstPage = skip - allQueries.limit <= 0;
      setSkip(isFirstPage ? 0 : skip - allQueries.limit);
    }
  };

  const loadResult = (queryId, queryName) => {
    if (queryId !== selectedQueryId) {
      dispatch(startQueryPoll({ queryId, queryName }));
    }
  };

  const handleFavQuery = (queryId,faved) => {
    dispatch(favQuery({ queryId, faved }));
  };

  const handleRenameQuery = (queryId, name, notes) => {
    dispatch(renameQuery({ queryId, name, notes }));
  };

  useEffect(() => {
    if (allQueries.results.length === 0) {
      dispatch(fetchAllQueries({ skip }));
    }
    setReadyToSort(true);
  }, []);

  useEffect(() => {
    if (readyToSort) {
      dispatch(sortAllQueries({ sortBy }));
      if (skip === 0) {
        dispatch(fetchAllQueries({ skip }));
      } else {
        setSkip(0);
      }
    }
  }, [sortBy]);

  useEffect(() => {
    if (readyToSort) {
      dispatch(fetchAllQueries({ skip }));
    }
  }, [skip]);

  return (
    <>
      {allQueries.error.hasError ? (
        <FetchError error={allQueries.error} />
      ) : (
        <QueryHistoryContext.Provider
          value={{
            showPageForward: skip < lastSkipIndex,
            showPageBack: skip > 0,
            pageForward,
            pageBack,
            results: allQueries.results,
            selectedQueryId,
            loadResult,
            handleFavQuery,
            handleRenameQuery,
            favTexts,
          }}
        >
          <QueryHistory
            isFetching={allQueries.isFetching}
            favTexts={favTexts}
            onSort={updateSort}
          />
        </QueryHistoryContext.Provider>
      )}
    </>
  );
};

WrappedQueryHistoryContainer.propTypes = {
  dispatch: PropTypes.func.isRequired,
  allQueries: PropTypes.shape(AllQueries.propTypes),
  selectedQueryId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  favTexts: PropTypes.shape({}),
};

WrappedQueryHistoryContainer.defaultProps = {
  allQueries: null,
  selectedQueryId: null,
  favTexts: null,
};

export const mapStateToProps = (state) => ({
  allQueries: state.allQueries,
  selectedQueryId: state.selectedQuery.queryId,
  favTexts: {
    queryFavingInstructions: state.networkConfig.queryFavingInstructions,
    favingIconInstructions: state.networkConfig.favingIconInstructions,
    favPlaceholderText: state.networkConfig.favPlaceholderText,
  },
});

const QueryHistoryContainer = connect(mapStateToProps)(
  WrappedQueryHistoryContainer
);
export { QueryHistoryContainer };
