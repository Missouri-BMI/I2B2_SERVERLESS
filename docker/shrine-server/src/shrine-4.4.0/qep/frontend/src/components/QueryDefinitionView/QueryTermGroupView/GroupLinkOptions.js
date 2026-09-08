import React, {useState} from "react";
import PropTypes from "prop-types";
import {
  Menu,
  MenuItem
} from "@material-ui/core";

import { GroupLinkType } from "models";
import "./GroupLinkOptions.scss";

export default function GroupLinkOptions({group, updateGroupOptions}) {
  const [anchorEl, setAnchorEl] = useState(null);
  const [openLinkMenu, setLinkOpenMenu] = useState(false);
  const [selectedLinkMenuItem, setSelectedLinkMenuItem] = useState(group.options.linkedBy);

  const handleOpenLinkMenu = (event) => {
    setAnchorEl(event.currentTarget);
    setLinkOpenMenu(!openLinkMenu);
  };

  const handleCloseLinkMenu = () => {
    setLinkOpenMenu(false);
  };

  const handleMenuItemClick = (event, groupLinkType) => {
    handleCloseLinkMenu();
    setSelectedLinkMenuItem(groupLinkType);

    group.options.linkedBy = groupLinkType;
    updateGroupOptions(group.id, group.options);
  };


  return (
      <div className={"GroupLinkOptions"}>
        {group.options.linkedBy == null &&
          <div title="panel timing: independent" className="noLink" onClick={handleOpenLinkMenu}>
            <div className="linkLetter">
              <i className="fa-solid fa-link-slash linkLetterIcon"/>
            </div>
          </div>
        }
        {group.options.linkedBy === GroupLinkType.SameEncounter &&
          <div title="panel timing: same encounter" className="encLink" onClick={handleOpenLinkMenu}>
            <div className="linkLetter">
              <i className="fa-solid fa-link linkLetterIcon"/>e
            </div>
          </div>
        }
        {group.options.linkedBy === GroupLinkType.SameInstance &&
          <div title="panel timing: same instance" className="instLink" onClick={handleOpenLinkMenu}>
            <div className="linkLetter">
              <i className="fa-solid fa-link linkLetterIcon"/>i
            </div>
          </div>
        }

        <Menu
          className={"LinkTypeMenu"}
          anchorEl={anchorEl}
          getContentAnchorEl={null}
          anchorOrigin={{
            vertical: 'bottom',
            horizontal: 'right',
          }}
          transformOrigin={{
            vertical: 'top',
            horizontal: 'right',
          }}
          keepMounted
          open={openLinkMenu}
          onClose={handleCloseLinkMenu}
        >
          <MenuItem onClick={(event) => handleMenuItemClick(event, GroupLinkType.SameEncounter)} selected={selectedLinkMenuItem === GroupLinkType.SameEncounter}>same encounter</MenuItem>
          <MenuItem onClick={(event) => handleMenuItemClick(event, GroupLinkType.SameInstance)}  selected={selectedLinkMenuItem === GroupLinkType.SameInstance}>same instance</MenuItem>
          {selectedLinkMenuItem !== null && <MenuItem onClick={(event) => handleMenuItemClick(event, null)}>unlink</MenuItem>}
        </Menu>
      </div>
  );
};

GroupLinkOptions.propTypes = {
  group: PropTypes.shape({}).isRequired,
  updateGroupOptions: PropTypes.func.isRequired
};
