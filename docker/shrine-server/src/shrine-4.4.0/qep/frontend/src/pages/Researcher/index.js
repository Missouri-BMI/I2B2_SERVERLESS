import { connect } from "react-redux";
import React, { useState, useContext } from "react";
import PropTypes from "prop-types";

import {
  QueryResultContainer,
  OntologyContainer,
  QueryHistoryContainer,
  QueryDefinitionContainer,
  IdleTimer,
  TermsOfUse,
} from "components";
import { ViewMode, ViewModeTypes } from "models";
import { MainContent } from "./MainContent";
import { ResearcherContext } from "./ResearcherContext";

export const WrappedResearcher = ({ viewMode }) => {
  const [forceCloseTutorial, setForceCloseTutorial] = useState(false);
  const forceCloseTutorialOnTimeout = () => {
    setForceCloseTutorial(true);
  };

  return (
    <div className="container Researcher">
      {viewMode.type === ViewModeTypes.QUERY_DEFINITION ? (
        <ResearcherContext.Provider
          value={{
            forceCloseTutorial,
          }}
        >
          <MainContent
            LeftContent={OntologyContainer}
            RightContent={QueryDefinitionContainer}
          />
        </ResearcherContext.Provider>
      ) : (
        <MainContent
          LeftContent={QueryHistoryContainer}
          RightContent={QueryResultContainer}
        />
      )}
      <IdleTimer onTimeoutWarning={forceCloseTutorialOnTimeout} />
      <TermsOfUse />
    </div>
  );
};

WrappedResearcher.propTypes = {
  viewMode: PropTypes.shape(ViewMode.propTypes).isRequired,
};

const mapStateToProps = ({ viewMode }) => ({ viewMode });
const Researcher = connect(mapStateToProps)(WrappedResearcher);

export { Researcher };
