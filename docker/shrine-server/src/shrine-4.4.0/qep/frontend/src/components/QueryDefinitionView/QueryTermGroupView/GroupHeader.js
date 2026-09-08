import React from "react";
import PropTypes from "prop-types";

import { QueryTermGroupStatusTypes } from "models";
import "./GroupHeader.scss";
import GroupLinkOptions from "./GroupLinkOptions";
import GroupTypeOptions from "./GroupTypeOptions";
import {AccessTime} from "@material-ui/icons";

export default function GroupHeader({
                                      onRadioChange,
                                      activeClass,
                                      onClearAllTermsClicked,
                                      hasChildren,
                                      status,
                                      timelineOptionDisabled,
                                      group,
                                      onOptionsChange
                                    }) {
  return (
    <div className={"GroupHeader"}>
      <GroupTypeOptions
        onRadioChange={onRadioChange}
        status={status}
        activeClass={activeClass}
        onClearAllTermsClicked={onClearAllTermsClicked}
        hasChildren={hasChildren}
        timelineOptionDisabled={timelineOptionDisabled}
      />
      {status !== QueryTermGroupStatusTypes.TIMELINE ?
        <GroupLinkOptions group={group} updateGroupOptions={onOptionsChange}/>
        : <AccessTime className="event-icon" />
      }
    </div>
  );
};

GroupHeader.propTypes = {
  onRadioChange: PropTypes.func.isRequired,
  activeClass: PropTypes.string.isRequired,
  onClearAllTermsClicked: PropTypes.func.isRequired,
  hasChildren: PropTypes.bool.isRequired,
};
