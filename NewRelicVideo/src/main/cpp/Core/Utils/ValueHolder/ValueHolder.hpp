//
//  ValueHolder.hpp
//  NewRelicVideo
//
//  Created by Andreu Santaren on 22/10/2018.
//  Copyright © 2018 New Relic Inc. All rights reserved.
//

#ifndef ValueHolder_hpp
#define ValueHolder_hpp

#include <stdio.h>
#include <string>
#include <vector>

class ValueHolder {
public:
    typedef enum {
        ValueHolderTypeInt,
        ValueHolderTypeFloat,
        ValueHolderTypeString,
        ValueHolderTypeEmpty
    } ValueHolderType;
    
private:
    ValueHolderType valueType;
    std::string valueString;
    long valueInt;
    double valueFloat;
    
public:
    ValueHolder();
    ValueHolder(std::string);
    ValueHolder(long);
    ValueHolder(int);
    ValueHolder(double);
    
    ValueHolderType getValueType();
    std::string getValueString();
    long getValueInt();
    double getValueFloat();
};

#endif /* ValueHolder_hpp */
