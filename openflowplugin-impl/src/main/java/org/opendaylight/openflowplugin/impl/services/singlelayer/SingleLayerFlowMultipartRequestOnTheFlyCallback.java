/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.openflowplugin.impl.services.singlelayer;

import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.Optional;
import org.opendaylight.openflowplugin.api.openflow.device.DeviceContext;
import org.opendaylight.openflowplugin.api.openflow.device.DeviceInfo;
import org.opendaylight.openflowplugin.api.openflow.device.RequestContext;
import org.opendaylight.openflowplugin.api.openflow.statistics.ofpspecific.EventIdentifier;
import org.opendaylight.openflowplugin.impl.common.MultipartReplyTranslatorUtil;
import org.opendaylight.openflowplugin.impl.datastore.MultipartWriterProvider;
import org.opendaylight.openflowplugin.impl.services.AbstractMultipartRequestOnTheFlyCallback;
import org.opendaylight.openflowplugin.impl.statistics.StatisticsGatheringUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.statistics.rev130819.multipart.reply.multipart.reply.body.MultipartReplyFlowStats;
import org.opendaylight.yang.gen.v1.urn.opendaylight.multipart.types.rev170112.MultipartReply;
import org.opendaylight.yang.gen.v1.urn.opendaylight.multipart.types.rev170112.multipart.reply.MultipartReplyBody;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflow.common.types.rev130731.MultipartType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflow.protocol.rev130731.OfHeader;

public class SingleLayerFlowMultipartRequestOnTheFlyCallback<T extends OfHeader> extends AbstractMultipartRequestOnTheFlyCallback<T> {

    private final DeviceInfo deviceInfo;
    private boolean virgin = true;

    public SingleLayerFlowMultipartRequestOnTheFlyCallback(final RequestContext<List<T>> context, Class<?> requestType,
                                                           final DeviceContext deviceContext,
                                                           final EventIdentifier eventIdentifier,
                                                           final MultipartWriterProvider statisticsWriterProvider) {
        super(context, requestType, deviceContext, eventIdentifier, statisticsWriterProvider);
        deviceInfo = deviceContext.getDeviceInfo();
    }

    @Override
    protected boolean isMultipart(OfHeader result) {
        return result instanceof MultipartReply
            && MultipartReply.class.cast(result).getMultipartReplyBody() instanceof MultipartReplyFlowStats;
    }

    @Override
    protected boolean isReqMore(T result) {
        return MultipartReply.class.cast(result).isRequestMore();
    }

    @Override
    protected MultipartType getMultipartType() {
        return MultipartType.OFPMPFLOW;
    }

    @Override
    protected ListenableFuture<Optional<? extends MultipartReplyBody>> processStatistics(T result) {
        final ListenableFuture<Optional<? extends MultipartReplyBody>> future = Futures.transform(
            StatisticsGatheringUtils.deleteAllKnownFlows(
                getTxFacade(),
                deviceInfo
                    .getNodeInstanceIdentifier()
                    .augmentation(FlowCapableNode.class),
                !virgin),
            (Function<Void, Optional<? extends MultipartReplyBody>>) input -> MultipartReplyTranslatorUtil
                .translate(result, deviceInfo, null, null));

        if (virgin) {
            virgin = false;
        }

        return future;
    }

}
