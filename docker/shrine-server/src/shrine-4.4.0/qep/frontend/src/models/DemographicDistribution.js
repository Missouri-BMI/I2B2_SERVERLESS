import PropTypes from "prop-types";

import { DemographicDistributionResult } from "./DemographicDistributionResult";

export const DemographicDistribution = ({
  description = null,
  results = [],
} = {}) => ({
  description,
  results,
});

DemographicDistribution.propTypes = {
  description: PropTypes.string,
  results: PropTypes.arrayOf(DemographicDistributionResult).isRequired,
};
