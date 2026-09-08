import PropTypes from "prop-types";

export const ResultMetadata = ({
  obfuscatingParameters = null,
  custom=null
} = {}) => ({
    obfuscatingParameters,
  custom
});

ResultMetadata.propTypes = {
  obfuscatingParameters: PropTypes.object,
  custom: PropTypes.object,
};
