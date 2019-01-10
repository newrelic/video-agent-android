//
//  DictionaryMerge.cpp
//  NewRelicVideo
//
//  Created by Andreu Santaren on 23/10/2018.
//  Copyright © 2018 New Relic Inc. All rights reserved.
//

#include "DictionaryMerge.hpp"
#include "ValueHolder.hpp"

// Merge dictionary src into dst. If key exist in both dst and src, keep the src value.
std::map<std::string, ValueHolder> DictionaryMerge::merge(std::map<std::string, ValueHolder> src, std::map<std::string, ValueHolder> dst) {
    for (auto& kv : src) dst[kv.first] = kv.second;
    return dst;
}
