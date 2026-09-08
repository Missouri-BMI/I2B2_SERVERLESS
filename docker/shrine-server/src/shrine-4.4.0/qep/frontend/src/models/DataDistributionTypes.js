import PropTypes from "prop-types";
import { Error } from "./Error";

export const DataDistributionTypes = ({
  allOutputTypes = {},
  isFetching = false,
  isLoaded = false,
  error = Error()
} = {}) => ({ allOutputTypes, isFetching, isLoaded, error });

DataDistributionTypes.propTypes = {
  isFetching: PropTypes.bool,
  isLoaded: PropTypes.bool,
  error: PropTypes.shape(Error.propTypes),
  allOutputTypes: PropTypes.shape({}),
};
