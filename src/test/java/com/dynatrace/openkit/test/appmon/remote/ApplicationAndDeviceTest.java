/***************************************************
 * (c) 2016-2017 Dynatrace LLC
 *
 * @author: Christian Schwarzbauer
 */
package com.dynatrace.openkit.test.appmon.remote;

import java.util.ArrayList;

import org.junit.Ignore;
import org.junit.Test;

import com.dynatrace.openkit.test.TestHTTPClient.Request;
import com.dynatrace.openkit.test.shared.ApplicationAndDeviceTestShared;

@Ignore("Integration tests are ignored")
public class ApplicationAndDeviceTest extends AbstractRemoteAppMonTest {

	@Test
	public void test() {
		ApplicationAndDeviceTestShared.test(openKit, TEST_IP);

		ArrayList<Request> sentRequests = openKitTestImpl.getSentRequests();
		validateDefaultRequests(sentRequests, null);
	}

}
