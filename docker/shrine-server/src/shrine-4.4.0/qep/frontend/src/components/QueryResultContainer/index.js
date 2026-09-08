import { connect } from "react-redux";
import React from "react";
import PropTypes from "prop-types";

import "./QueryResultContainer.scss";
import { FetchError } from "components";
import { SelectedQuery } from "models";
import { exportQueryToCSV } from "actions";
import { QueryResultView } from "components/QueryResultView";
import { addSiteSort, startQueryPoll, reloadQuery } from "../../actions";

export const WrappedQueryResultContainer = ({ selectedQuery, dispatch }) => {
  // export query is not yet available in the view.
  const exportQuery = () => {
    dispatch(exportQueryToCSV(selectedQuery));
  };

  const handleSiteSort = (sortSiteBy) => {
    // Call action to update QueryResult sortSiteBy
    dispatch(addSiteSort({ sortSiteBy }));
    // Call fetchQueryUpdate saga with the right network id
    dispatch(
      startQueryPoll({
        queryId: selectedQuery.queryId,
        dataVersion: -1,
        queryName: selectedQuery.queryResult.queryName,
        queryAsHtmlString: selectedQuery.queryResult.queryAsHtmlString,
        changeDate: selectedQuery.queryResult.changeDate,
        demographicDistribution: selectedQuery.demographicDistribution
      })
    );
  };

  const handleReloadQuery = (queryId) => {
    dispatch(reloadQuery({ queryId }));
  };

  return selectedQuery.error.hasError ? (
    <FetchError error={selectedQuery.error} />
  ) : (
    <QueryResultView
      selectedQuery={selectedQuery}
      onSiteSort={handleSiteSort}
      onReloadQuery={handleReloadQuery}
    />
  );
};

WrappedQueryResultContainer.propTypes = {
  selectedQuery: PropTypes.shape(SelectedQuery.propTypes),
  dispatch: PropTypes.func.isRequired,
};

WrappedQueryResultContainer.defaultProps = {
  selectedQuery: null,
};

const mapStateToProps = ({ selectedQuery, viewMode }) => ({
  selectedQuery,
  viewMode,
});
const QueryResultContainer = connect(mapStateToProps)(
  WrappedQueryResultContainer
);
export { QueryResultContainer };
