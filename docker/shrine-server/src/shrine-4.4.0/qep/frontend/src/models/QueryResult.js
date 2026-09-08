import PropTypes from "prop-types";

import { Fav } from "./Fav";
import { Error } from "./Error";
import { ErrorDetailInfo } from "./ErrorDetailInfo";

export const QueryResult = ({
  queryId = null,
  queryName = null,
  queryNotes = null,
  dateCreated = null,
  changeDate = null,
  queryFaved = null,
  status = null,
  internalStatus = null,
  isQueryError = false,
  isResultsError = false,
  isQueryComplete = false,
  observed = false,
  queryAsHtmlString = null,
  problemDigest = null
} = {}) => {
  const errorDetailInfo = ErrorDetailInfo({ problemDigest, status });
  return {
    queryId,
    queryName,
    queryNotes,
    dateCreated,
    changeDate,
    queryFaved,
    internalStatus,
    isError: isQueryError === true || isResultsError === true,
    isQueryComplete,
    observed,
    queryAsHtmlString,
    errorDetailInfo
  };
};

QueryResult.propTypes = {
  queryId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  queryName: PropTypes.string,
  queryNotes: PropTypes.string,
  dateCreated: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  changeDate: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  queryFaved: PropTypes.shape(Fav),
  internalStatus: PropTypes.string,
  isError: PropTypes.bool,
  isQueryComplete: PropTypes.bool,
  observed: PropTypes.bool,
  queryAsHtmlString: PropTypes.string,
  errorDetailInfo: PropTypes.shape(ErrorDetailInfo.propTypes)
};
