//
//  TimestampHolder.cpp
//  NewRelicVideo
//
//  Created by Andreu Santaren on 29/10/2018.
//  Copyright © 2018 New Relic Inc. All rights reserved.
//

#include "TimestampHolder.hpp"
#include "CAL.hpp"

TimestampHolder::TimestampHolder(double timestamp) {
    mainTimestamp = timestamp;
    externalTimestamp = 0;
}

double TimestampHolder::timestamp() {
    if (externalTimestamp > 0.0) {
        return externalTimestamp;
    }
    else {
        return mainTimestamp;
    }
}

void TimestampHolder::setMain(double timestamp) {
    mainTimestamp = timestamp;
}

void TimestampHolder::setExternal(double timestamp) {
    externalTimestamp = timestamp;
}

double TimestampHolder::sinceMillis() {
    if (timestamp() > 0.0) {
        return 1000.0f * timeSince(timestamp());
    }
    else {
        return 0.0;
    }
}
