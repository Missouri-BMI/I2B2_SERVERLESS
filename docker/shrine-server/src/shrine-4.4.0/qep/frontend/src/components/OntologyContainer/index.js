import React, { useEffect } from "react";
import PropTypes from "prop-types";
import { connect } from "react-redux";

import { Ontology } from "models";
import {
  fetchOntologyRoot,
  fetchFilteredOntology,
  removeOntologyFilter,
  fetchFilterOptions,
} from "actions";
import { getBaseUrl, secureFetch } from "utilities";
import { OntologyView } from "components";

export const WrappedOntologyContainer = ({ ontology, dispatch }) => {
  const setTreeFilter = (searchString, filterData) => {
    dispatch(fetchFilteredOntology({ searchString, filterData }));
  };

  const removeTreeFilter = () => {
    dispatch(removeOntologyFilter());
  };

  const fetchChildren = (path) => {
    const url = `${getBaseUrl()}ontology/children`;
    const headers = {
      "Content-Type": "application/json",
    };
    const fetchConfig = {
      headers,
      method: "POST",
      body: JSON.stringify({ path }),
    };

    return secureFetch(url, fetchConfig);
  };

  useEffect(() => {
    if (!ontology.root.length) {
      dispatch(fetchOntologyRoot());
    }
    if (ontology.filterOptions === null) {
      dispatch(fetchFilterOptions());
    }
  }, []);

  return (
    <OntologyView
      ontology={ontology}
      fetchChildren={fetchChildren}
      onSearch={setTreeFilter}
      onSearchReset={removeTreeFilter}
    />
  );
};

export const mapStateToProps = ({ ontology }) => ({ ontology });
const OntologyContainer = connect(mapStateToProps)(WrappedOntologyContainer);
export { OntologyContainer };

WrappedOntologyContainer.propTypes = {
  ontology: PropTypes.shape(Ontology.propTypes).isRequired,
  dispatch: PropTypes.func.isRequired,
};
