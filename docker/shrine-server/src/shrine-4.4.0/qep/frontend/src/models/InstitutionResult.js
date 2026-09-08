import PropTypes from "prop-types";

import { Error } from "./Error";
import { ErrorDetailInfo } from "./ErrorDetailInfo";
import {ResultMetadata} from "./ResultMetadata";

const sortBy = {
  ASC: "asc",
  DESC: "desc",
};

export const InstitutionResultOptions = {
  sortBy,
};

export const InstitutionResult = ({
  adapterNode: institutionName = null,
  count = null,
  status = null,
  internalStatus = null,
  statusMessage = null,
  breakdowns = [],
  problemDigest = null,
  resultMetadata = ResultMetadata()
} = {}) => {
  const errorDetailInfo = ErrorDetailInfo({ problemDigest, status });
  return {
    institutionName,
    count,
    status,
    internalStatus,
    statusMessage,
    breakdowns,
    errorDetailInfo,
    resultMetadata
  };
};

InstitutionResult.propTypes = {
  errorDetailInfo: PropTypes.shape(ErrorDetailInfo.propTypes),
  institutionName: PropTypes.string,
  count: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  status: PropTypes.string,
  internalStatus: PropTypes.string,
  statusMessage: PropTypes.string,
  error: PropTypes.shape(Error.propTypes),
  resultMetadata: PropTypes.shape(ResultMetadata.propTypes)
};
