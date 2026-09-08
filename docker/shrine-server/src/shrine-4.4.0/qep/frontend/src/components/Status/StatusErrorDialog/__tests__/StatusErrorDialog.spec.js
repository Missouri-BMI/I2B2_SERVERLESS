// emailTo, subject

import React from "react";
import { shallow } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import { ErrorDetailInfo } from "models";
import { WrappedStatusErrorDialog } from "..";

const mockEmail = "mock@mockEmail.com";
const mockData = ErrorDetailInfo({
  resultId: 2876678608812141422,
  networkQueryId: 2252723558487049660,
  instanceId: -1,
  adapterNode: "shrine-qa2",
  resultType: null,
  count: -1,
  status: "Error From CRC",
  internalStatus: "Error From CRC",
  statusMessage: "Unanticipated exception with response from CRC.",
  changeDate: 1563377201218,
  breakdowns: [],
  problemDigest: {
    codec: "net.shrine.adapter.ExceptionWhileLoadingCrcResponse",
    stampText: "Wed Jul 17 11:26:40 EDT 2019 on shrine-qa2.catalyst Adapter",
    summary: "Unanticipated exception with response from CRC.",
    description:
      "Child node with label 'message_body' not found in XML '<html><head><title>Error</title></head><body>404 - Not Found</body></html>' while parsing the response from the CRC.",
    detailsText:
      "<details>Response is &lt;html&gt;&lt;head&gt;&lt;title&gt;Error&lt;/title&gt;&lt;/head&gt;&lt;body&gt;404 - Not Found&lt;/body&gt;&lt;/html&gt;\n<exception>\n          <name>net.shrine.problem.ExceptionDigest</name>\n          <message>Some(Child node with label 'message_body' not found in XML '&lt;html&gt;&lt;head&gt;&lt;title&gt;Error&lt;/title&gt;&lt;/head&gt;&lt;body&gt;404 - Not Found&lt;/body&gt;&lt;/html&gt;')</message>\n          <stacktrace>\n            <line>net.shrine.xml.MissingChildNodeException$.apply(NodeSeqEnrichments.scala:53)</line><line>net.shrine.xml.NodeSeqEnrichments$Strictness$HasStrictNodeSeqEnrichmentsForAttempts$$anonfun$withChild$extension$2.apply(NodeSeqEnrichments.scala:32)</line><line>net.shrine.xml.NodeSeqEnrichments$Strictness$HasStrictNodeSeqEnrichmentsForAttempts$$anonfun$withChild$extension$2.apply(NodeSeqEnrichments.scala:28)</line><line>scala.util.Success.flatMap(Try.scala:231)</line><line>net.shrine.xml.NodeSeqEnrichments$Strictness$HasStrictNodeSeqEnrichmentsForAttempts$.withChild$extension(NodeSeqEnrichments.scala:28)</line><line>net.shrine.xml.NodeSeqEnrichments$Strictness$HasStrictNodeSeqEnrichments$.withChild$extension(NodeSeqEnrichments.scala:22)</line><line>net.shrine.protocol.ErrorResponse$.net$shrine$protocol$ErrorResponse$$parseFormatB$1(ErrorResponse.scala:89)</line><line>net.shrine.protocol.ErrorResponse$$anonfun$fromI2b2$1.applyOrElse(ErrorResponse.scala:101)</line><line>net.shrine.protocol.ErrorResponse$$anonfun$fromI2b2$1.applyOrElse(ErrorResponse.scala:99)</line><line>scala.runtime.AbstractPartialFunction.apply(AbstractPartialFunction.scala:36)</line><line>scala.util.Failure.recoverWith(Try.scala:203)</line><line>net.shrine.protocol.ErrorResponse$.fromI2b2(ErrorResponse.scala:99)</line><line>net.shrine.adapter.CrcAdapter$$anonfun$2$$anonfun$apply$1.applyOrElse(CrcAdapter.scala:38)</line><line>net.shrine.adapter.CrcAdapter$$anonfun$2$$anonfun$apply$1.applyOrElse(CrcAdapter.scala:36)</line><line>scala.runtime.AbstractPartialFunction.apply(AbstractPartialFunction.scala:36)</line><line>scala.util.Failure$$anonfun$recover$1.apply(Try.scala:216)</line><line>scala.util.Try$.apply(Try.scala:192)</line><line>scala.util.Failure.recover(Try.scala:216)</line><line>net.shrine.adapter.CrcAdapter$$anonfun$2.apply(CrcAdapter.scala:36)</line><line>net.shrine.adapter.CrcAdapter$$anonfun$2.apply(CrcAdapter.scala:35)</line><line>scala.util.Success.flatMap(Try.scala:231)</line><line>net.shrine.adapter.CrcAdapter.parseShrineErrorResponseWithFallback(CrcAdapter.scala:35)</line><line>net.shrine.adapter.RunQueryAdapter.net$shrine$adapter$RunQueryAdapter$$runQueryInCrcAndStoreResults(RunQueryAdapter.scala:138)</line><line>net.shrine.adapter.RunQueryAdapter$$anonfun$5.apply(RunQueryAdapter.scala:95)</line><line>net.shrine.adapter.RunQueryAdapter$$anonfun$5.apply(RunQueryAdapter.scala:85)</line><line>scala.util.Success$$anonfun$map$1.apply(Try.scala:237)</line><line>scala.util.Try$.apply(Try.scala:192)</line><line>scala.util.Success.map(Try.scala:237)</line><line>net.shrine.adapter.RunQueryAdapter.runQueryForExpectedResult(RunQueryAdapter.scala:85)</line><line>net.shrine.adapter.RunQueryAdapter$$anonfun$startRunQueryForExpectedResult$1.apply$mcV$sp(RunQueryAdapter.scala:67)</line><line>net.shrine.adapter.RunQueryAdapter$$anonfun$startRunQueryForExpectedResult$1.apply(RunQueryAdapter.scala:67)</line><line>net.shrine.adapter.RunQueryAdapter$$anonfun$startRunQueryForExpectedResult$1.apply(RunQueryAdapter.scala:67)</line><line>cats.effect.internals.IORunLoop$.cats$effect$internals$IORunLoop$$loop(IORunLoop.scala:87)</line><line>cats.effect.internals.IORunLoop$RestartCallback.signal(IORunLoop.scala:351)</line><line>cats.effect.internals.IORunLoop$RestartCallback.apply(IORunLoop.scala:372)</line><line>cats.effect.internals.IORunLoop$RestartCallback.apply(IORunLoop.scala:312)</line><line>cats.effect.internals.IOShift$Tick.run(IOShift.scala:36)</line><line>java.util.concurrent.ForkJoinTask$RunnableExecuteAction.exec(ForkJoinTask.java:1402)</line><line>java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:289)</line><line>java.util.concurrent.ForkJoinPool$WorkQueue.runTask(ForkJoinPool.java:1056)</line><line>java.util.concurrent.ForkJoinPool.runWorker(ForkJoinPool.java:1692)</line><line>java.util.concurrent.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:157)</line>\n          </stacktrace>\n        </exception></details>",
    exceptionDigest: null,
    epoch: 0
  }
});

describe("WrappedStatusErrorDialog", () => {
  let openState = true;
  const onClose = () => (openState = false);
  const props = {
    emailTo: mockEmail,
    open: openState,
    onClose: onClose,
    data: mockData
  };

  const output = shallow(<WrappedStatusErrorDialog {...props} />);

  it("should render correctly", () => {
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
