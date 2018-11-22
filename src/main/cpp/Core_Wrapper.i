// Module Name
%module Core_Wrapper

// Included stuff
%{
#include "ValueHolder.hpp"
#include "TrackerCore.hpp"
#include "ContentsTrackerCore.hpp"
#include "AdsTrackerCore.hpp"
#include "PlaybackAutomatCore.hpp"
%}

// The prototypes to convert
%include "std_string.i"
%include "std_map.i"
%include "Core/Trackers/TrackerCore.hpp"
%include "Core/Trackers/ContentsTrackerCore.hpp"
%include "Core/Trackers/AdsTrackerCore.hpp"
%include "Core/Utils/ValueHolder/ValueHolder.hpp"
%include "Core/CoreDefs.hpp"
