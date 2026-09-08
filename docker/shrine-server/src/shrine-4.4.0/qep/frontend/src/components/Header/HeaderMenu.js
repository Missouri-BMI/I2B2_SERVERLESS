import React, { useState, useEffect } from "react";
import PropTypes from "prop-types";
import { CopyToClipboard } from "react-copy-to-clipboard";
import { Tooltip, Menu, MenuItem, Divider, Button } from "@material-ui/core";
import isURL from 'validator/lib/isURL';

import { User, NetworkConfig } from "models";
import { getBaseUrl, secureFetch, getDatetime } from "utilities";
import {clearLogin} from "actions";
import "./HeaderMenu.scss";

export function HeaderMenu({ dispatch, user, networkConfig }) {
  const [versionData, setVersionData] = useState(null);
  const [anchorEl, setAnchorEl] = useState(null);
  const [copied, setCopied] = useState(false);
  const [toolTipOpen, setToolTipOpen] = useState(false);

  const formatToDate = (dateString) =>
    getDatetime(new Date(dateString).getTime());

  const fetchVersionData = () => {
    const staticDataServiceUrl = `${getBaseUrl()}staticData`;
    const url = `${staticDataServiceUrl}/version`;
    const fetchConfig = {
      method: "GET",
    };

    return secureFetch(url, fetchConfig);
  };

  const handleClick = (event) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
    handleTooltipClose();
  };

  const handleCopy = () => {
    setCopied(true);
    handleTooltipOpen();
  };

  const handleTooltipClose = () => {
    setToolTipOpen(false);
  };

  const handleTooltipOpen = () => {
    setToolTipOpen(true);
  };

  const handleLogout = () => {
    dispatch(clearLogin());
  };

  useEffect(() => {
    (async () => {
      const result = await fetchVersionData();
      setVersionData(result.data);
    })();
  }, []);

  const sanitizeUrl = (urlStr) => {
    return urlStr.replace('"', '');
  };

  const getMenuItemsFromMap = (map) => {
    return map
      ? Object.entries(map).map(([key, value]) => {
        const urlStr = sanitizeUrl(value);
        return (
          isURL(urlStr) && <MenuItem onClick={handleClose} className="help-link" key={key}>
            <div className="menu-item-content">
              <a href={urlStr} target="_blank" rel="noopener noreferrer">
                {key}
              </a>
            </div>
          </MenuItem>
        )})
      : null;
  };

  return (
    <div className="HeaderMenu">
      <Tooltip title="Account" arrow classes={{ tooltip: "HeaderMenuTooltip" }}>
        <i
          className="fa-solid fa-circle-user"
          aria-hidden="true"
          onClick={handleClick}
        />
      </Tooltip>

      <Menu
        className="header-menu-options"
        anchorEl={anchorEl}
        keepMounted
        open={Boolean(anchorEl)}
        onClose={handleClose}
      >
        <MenuItem>
          <div className="menu-item-content">
            <div className="fa fa-user-circle-o user-icon" aria-hidden="true" />
            <div>{user.username}</div>
            <div className="domain">{networkConfig.domain}</div>
          </div>
        </MenuItem>
        <Divider variant="middle" />
        {getMenuItemsFromMap(networkConfig.helpLinks)}
        <Divider variant="middle" />
        <MenuItem onClick={handleClose}>
          <div className="menu-item-content">
            <Button variant="outlined" onClick={handleLogout}>
              Sign Out
            </Button>
          </div>
        </MenuItem>
        {versionData && (
          <span>
            <Divider variant="middle" />
            <CopyToClipboard
              onCopy={handleCopy}
              text={`${versionData.currentVersion} (${
                versionData.buildId
              }) built on ${formatToDate(versionData.buildDate)}`}
            >
              <Tooltip
                open={toolTipOpen}
                title="Copied to clipboard"
                placement="top"
                PopperProps={{
                  popperOptions: {
                    modifiers: {
                      offset: {
                        enabled: true,
                        offset: "0px, -18px",
                      },
                    },
                  },
                }}
              >
                <MenuItem>
                  <div className="version-info">
                    <div className="menu-item-content">
                      {versionData.currentVersion} ({versionData.buildId})
                    </div>
                    <div className="menu-item-content">
                      built on {formatToDate(versionData.buildDate)}
                    </div>
                  </div>
                </MenuItem>
              </Tooltip>
            </CopyToClipboard>
          </span>
        )}
      </Menu>
    </div>
  );
}

HeaderMenu.propTypes = {
  dispatch: PropTypes.func.isRequired,
  user: PropTypes.shape(User.propTypes).isRequired,
  networkConfig: PropTypes.shape(NetworkConfig.propTypes).isRequired,
};
