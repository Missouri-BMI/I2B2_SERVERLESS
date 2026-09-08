import PropTypes from "prop-types";

import { Error } from "./Error";
import { InstitutionResult } from "./InstitutionResult";
import { QueryResult } from "./QueryResult";
import { DemographicDistribution } from "./DemographicDistribution";

export const SelectedQuery = ({
  queryId = null,
  queryResult = QueryResult(),
  institutionResults = [],
  demographicDistribution = [],
  siteCount = 0,
  patientCount = 0,
  isComplete = false,
  dataVersion = -1,
  statusMsg = "There are no query results to display. Please select or run a query.",
  sortSiteBy = "site.asc",
  error = Error(),
  isFetching = false,
  isNewDataFetch = false,
  csvDownloadURL = null,
  countCsvDownloadURL = null,
} = {}) => ({
  queryId,
  queryResult,
  institutionResults,
  demographicDistribution,
  siteCount,
  patientCount,
  isComplete,
  dataVersion,
  statusMsg,
  sortSiteBy,
  error,
  isFetching,
  isNewDataFetch,
  csvDownloadURL,
  countCsvDownloadURL,
});

SelectedQuery.propTypes = {
  queryId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  queryResult: PropTypes.shape(QueryResult.propTypes),
  institutionResults: PropTypes.arrayOf(
    PropTypes.shape(InstitutionResult.propTypes)
  ),
  demographicDistribution: PropTypes.arrayOf(
    PropTypes.shape(DemographicDistribution.propTypes)
  ),
  siteCount: PropTypes.number.isRequired,
  patientCount: PropTypes.number.isRequired,
  isComplete: PropTypes.bool.isRequired,
  dataVersion: PropTypes.number.isRequired,
  statusMsg: PropTypes.string.isRequired,
  sortSiteBy: PropTypes.string.isRequired,
  error: PropTypes.shape(Error.propTypes),
  isFetching: PropTypes.bool.isRequired,
};
