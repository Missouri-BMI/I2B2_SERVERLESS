import React from 'react';
import { shallow } from 'enzyme';
import { shallowToJson } from 'enzyme-to-json';

import { InstitutionResult } from '../../../models';
import { InstitutionResultStatus } from '../InstitutionResultStatus';

describe('InstitutionResultStatus', () => {

  const props = {
    result: InstitutionResult({
      internalStatus: 'mock internal status',
    }),
  };

  it('should render correctly', () => {
    const output = shallow(
      <InstitutionResultStatus {...props} />,
    );
    expect(shallowToJson(output)).toMatchSnapshot();
  });

  it('Should render a patient count for "result from crc" status', () => {
    const newProps = {
      result: InstitutionResult({ internalStatus: 'RESULT FROM CRC', count: 10000 }),
    };
    const output = shallow(
      <InstitutionResultStatus {...newProps} />,
    );
    expect(output.find('.PatientCount').length).toBe(1);
  });
  it('Should render a patient count for "FINISHED" status', () => {
    const newProps = {
      result: InstitutionResult({ internalStatus: 'FINISHED', count: 999 }),
    };
    const output = shallow(
      <InstitutionResultStatus {...newProps} />,
    );
    expect(output.find('.PatientCount').length).toBe(1);
  });
  it('Should render Processing At Site for "ID ASSIGNED" status', () => {
    const newProps = {
      result: InstitutionResult({ internalStatus: 'ID ASSIGNED' }),
    };
    const output = shallow(
      <InstitutionResultStatus {...newProps} />,
    );
    expect(output.find('.ProcessingAtSite').length).toBe(1);
  });
  it('Should render Processing At Site for "SENT TO ADAPTER" status', () => {
    const newProps = {
      result: InstitutionResult({ internalStatus: 'SENT TO ADAPTER' }),
    };
    const output = shallow(
      <InstitutionResultStatus {...newProps} />,
    );
    expect(output.find('.ProcessingAtSite').length).toBe(1);
  });
  it('Should render Processing At Site for "RECEIVED BY ADAPTER" status', () => {
    const newProps = {
      result: InstitutionResult({ internalStatus: 'RECEIVED BY ADAPTER' }),
    };
    const output = shallow(
      <InstitutionResultStatus {...newProps} />,
    );
    expect(output.find('.ProcessingAtSite').length).toBe(1);
  });
  it('Should render Processing At Site for "READY TO SUBMIT" status', () => {
    const newProps = {
      result: InstitutionResult({ internalStatus: 'READY TO SUBMIT' }),
    };
    const output = shallow(
      <InstitutionResultStatus {...newProps} />,
    );
    expect(output.find('.ProcessingAtSite').length).toBe(1);
  });
  it('Should render Processing At Site for "SUBMITTED TO CRC" status', () => {
    const newProps = {
      result: InstitutionResult({ internalStatus: 'SUBMITTED TO CRC' }),
    };
    const output = shallow(
      <InstitutionResultStatus {...newProps} />,
    );
    expect(output.find('.ProcessingAtSite').length).toBe(1);
  });
  it('Should render Processing At Site for "ERROR IN SHRINE" status', () => {
    const newProps = {
      result: InstitutionResult({ internalStatus: 'ERROR IN SHRINE' }),
    };
    const output = shallow(
      <InstitutionResultStatus {...newProps} />,
    );
    expect(output.find('.SiteError').length).toBe(1);
  });
  it('Should render Processing At Site for "UKNOWN WHILE QUEUED BY CRC" status', () => {
    const newProps = {
      result: InstitutionResult({ internalStatus: 'UKNOWN WHILE QUEUED BY CRC' }),
    };
    const output = shallow(
      <InstitutionResultStatus {...newProps} />,
    );
    expect(output.find('.SiteError').length).toBe(1);
  });
  it('Should render Processing At Site for "ERROR FROM CRC" status', () => {
    const newProps = {
      result: InstitutionResult({ internalStatus: 'ERROR FROM CRC' }),
    };
    const output = shallow(
      <InstitutionResultStatus {...newProps} />,
    );
    expect(output.find('.SiteError').length).toBe(1);
  });
  it('Should render Processing At Site for "UNKNOWN FINAL" status', () => {
    const newProps = {
      result: InstitutionResult({ internalStatus: 'UNKNOWN FINAL' }),
    };
    const output = shallow(
      <InstitutionResultStatus {...newProps} />,
    );
    expect(output.find('.SiteError').length).toBe(1);
  });
  it('Should render Processing At Site for "ERROR" status', () => {
    const newProps = {
      result: InstitutionResult({ internalStatus: 'ERROR' }),
    };
    const output = shallow(
      <InstitutionResultStatus {...newProps} />,
    );
    expect(output.find('.SiteError').length).toBe(1);
  });
  it('Should render Processing At Site for "PROCESSING" status', () => {
    const newProps = {
      result: InstitutionResult({ internalStatus: 'PROCESSING' }),
    };
    const output = shallow(
      <InstitutionResultStatus {...newProps} />,
    );
    expect(output.find('.SiteError').length).toBe(1);
  });
  it('Should render Processing At Site for "RUNNING" status', () => {
    const newProps = {
      result: InstitutionResult({ internalStatus: 'RUNNING' }),
    };
    const output = shallow(
      <InstitutionResultStatus {...newProps} />,
    );
    expect(output.find('.SiteError').length).toBe(1);
  });
  it('Should render Processing At Site for "QUEUED" status', () => {
    const newProps = {
      result: InstitutionResult({ internalStatus: 'QUEUED' }),
    };
    const output = shallow(
      <InstitutionResultStatus {...newProps} />,
    );
    expect(output.find('.SiteError').length).toBe(1);
  });
  it('Should render Processing At Site for "INCOMPLETE" status', () => {
    const newProps = {
      result: InstitutionResult({ internalStatus: 'INCOMPLETE' }),
    };
    const output = shallow(
      <InstitutionResultStatus {...newProps} />,
    );
    expect(output.find('.SiteError').length).toBe(1);
  });
  it('Should render Processing At Site for "HELD" status', () => {
    const newProps = {
      result: InstitutionResult({ internalStatus: 'HELD' }),
    };
    const output = shallow(
      <InstitutionResultStatus {...newProps} />,
    );
    expect(output.find('.SiteError').length).toBe(1);
  });
  it('Should render Processing At Site for "SMALL_QUEUE" status', () => {
    const newProps = {
      result: InstitutionResult({ internalStatus: 'SMALL_QUEUE' }),
    };
    const output = shallow(
      <InstitutionResultStatus {...newProps} />,
    );
    expect(output.find('.SiteError').length).toBe(1);
  });
  it('Should render Processing At Site for "TIMEDOUT" status', () => {
    const newProps = {
      result: InstitutionResult({ internalStatus: 'TIMEDOUT' }),
    };
    const output = shallow(
      <InstitutionResultStatus {...newProps} />,
    );
    expect(output.find('.SiteError').length).toBe(1);
  });
  it('Should render Processing At Site for "MEDIUM_QUEUE" status', () => {
    const newProps = {
      result: InstitutionResult({ internalStatus: 'MEDIUM_QUEUE' }),
    };
    const output = shallow(
      <InstitutionResultStatus {...newProps} />,
    );
    expect(output.find('.SiteError').length).toBe(1);
  });
  it('Should render Processing At Site for "LARGE_QUEUE" status', () => {
    const newProps = {
      result: InstitutionResult({ internalStatus: 'LARGE_QUEUE' }),
    };
    const output = shallow(
      <InstitutionResultStatus {...newProps} />,
    );
    expect(output.find('.SiteError').length).toBe(1);
  });
  it('Should render Processing At Site for "NO_MORE_QUEUE" status', () => {
    const newProps = {
      result: InstitutionResult({ internalStatus: 'NO_MORE_QUEUE' }),
    };
    const output = shallow(
      <InstitutionResultStatus {...newProps} />,
    );
    expect(output.find('.SiteError').length).toBe(1);
  });
  it('Should render Processing At Site for "MEDIUM_QUEUE_RUNNING" status', () => {
    const newProps = {
      result: InstitutionResult({ internalStatus: 'MEDIUM_QUEUE_RUNNING' }),
    };
    const output = shallow(
      <InstitutionResultStatus {...newProps} />,
    );
    expect(output.find('.SiteError').length).toBe(1);
  });
  it('Should render Processing At Site for "LARGE_QUEUE_RUNNING" status', () => {
    const newProps = {
      result: InstitutionResult({ internalStatus: 'LARGE_QUEUE_RUNNING' }),
    };
    const output = shallow(
      <InstitutionResultStatus {...newProps} />,
    );
    expect(output.find('.SiteError').length).toBe(1);
  });
  it('Should render Processing At Site for "HUB_WILL_SUBMIT" status', () => {
    const newProps = {
      result: InstitutionResult({ internalStatus: 'HUB_WILL_SUBMIT' }),
    };
    const output = shallow(
      <InstitutionResultStatus {...newProps} />,
    );
    expect(output.find('.SiteError').length).toBe(1);
  });
    it('Should render Processing At Site for "Delayed At Site" status', () => {
    const newProps = {
      result: InstitutionResult({ internalStatus: 'Delayed At Site' }),
    };
    const output = shallow(
      <InstitutionResultStatus {...newProps} />,
    );
    expect(output.find('.DelayedAtSite').length).toBe(1);
  });

});
