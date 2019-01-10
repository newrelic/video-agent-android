//
//  DictionaryMerge.hpp
//  NewRelicVideo
//
//  Created by Andreu Santaren on 23/10/2018.
//  Copyright © 2018 New Relic Inc. All rights reserved.
//

#ifndef DictionaryMerge_hpp
#define DictionaryMerge_hpp

#include <stdio.h>
#include <map>

class ValueHolder;

class DictionaryMerge {
public:
    static std::map<std::string, ValueHolder> merge(std::map<std::string, ValueHolder> src, std::map<std::string, ValueHolder> dst);
};


#endif /* DictionaryMerge_hpp */
