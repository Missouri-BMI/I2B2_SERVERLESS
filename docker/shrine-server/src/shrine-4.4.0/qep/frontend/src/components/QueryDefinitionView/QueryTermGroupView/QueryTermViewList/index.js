import React from "react";
import PropTypes from "prop-types";

import "./QueryTermViewList.scss";
import { QueryTerm } from "models";
import { QueryTermView } from "./QueryTermView";
import { QueryTermLabView } from "./LabValues";

export default function QueryTermViewList({
  terms,
  onDeleteTermClicked,
  updateTerm,
  fetchConceptInfo,
}) {
  return (
    <div className="DropArea">
      {terms.map((term, index) => {
        const Element = term.isLab ? QueryTermLabView : QueryTermView;
        return (
          <Element
            index={index}
            term={term}
            key={term.path}
            onDeleteTermClicked={onDeleteTermClicked}
            onConstraintChange={(newOptions) =>
              updateTerm(term.path, {
                constraint: { ...term.constraint, ...newOptions },
              })
            }
            fetchConceptInfo={fetchConceptInfo}
          />
        );
      })}
    </div>
  );
}

QueryTermViewList.propTypes = {
  terms: PropTypes.arrayOf(PropTypes.shape(QueryTerm.propTypes)).isRequired,
  onDeleteTermClicked: PropTypes.func.isRequired,
  updateTerm: PropTypes.func.isRequired,
  fetchConceptInfo: PropTypes.func.isRequired,
};
