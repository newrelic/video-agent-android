//
//  TimestampHolder.hpp
//  NewRelicVideo
//
//  Created by Andreu Santaren on 29/10/2018.
//  Copyright © 2018 New Relic Inc. All rights reserved.
//

#ifndef TimestampHolder_hpp
#define TimestampHolder_hpp

#include <stdio.h>

class TimestampHolder {
private:
    double mainTimestamp;
    double externalTimestamp;
public:
    TimestampHolder(double timestamp);
    double timestamp();
    void setMain(double timestamp);
    void setExternal(double timestamp);
    double sinceMillis();
};

#endif /* TimestampHolder_hpp */
