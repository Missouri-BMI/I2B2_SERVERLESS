import PropTypes from "prop-types";

export const DemographicDistributionResult = ({
  dataKey = null,
  value = null,
  noiseClamp = null,
  lowLimit
} = {}) => ({
  dataKey,
  value,
  noiseClamp,
  lowLimit
});

DemographicDistributionResult.propTypes = {
  dataKey: PropTypes.string,
  value: PropTypes.string,
  noiseClamp: PropTypes.number,
  lowLimit: PropTypes.number
};
