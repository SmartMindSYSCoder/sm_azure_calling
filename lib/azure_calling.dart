
import 'package:flutter/services.dart';

import 'call_join_type.dart';


class AzureCalling {

  final methodChannel = const MethodChannel('azure_calling');





  Future<void> startCall({CallJoinType callType=CallJoinType.groupId,required String token, String teamsLinkOrGroupId="",required String displayName,required String title,String subTitle='',bool skipSetupScreen=true,bool cameraOn=false,bool microphoneOn=false}) async {

    if(callType ==CallJoinType.groupId && isValidGroupId(teamsLinkOrGroupId) ){

      await methodChannel.invokeMethod<void>('startCall',{"token":token,"meetingLink":"","groupId":teamsLinkOrGroupId,"displayName":displayName,"title":title,"subTitle":subTitle,"skipSetupScreen":skipSetupScreen,"cameraOn":cameraOn,"microphoneOn":microphoneOn});


    }
  else  if(callType ==CallJoinType.teamsLink && isValidTeamsLink(teamsLinkOrGroupId) ){
      await methodChannel.invokeMethod<void>('startCall',{"token":token,"meetingLink":teamsLinkOrGroupId,"groupId":"","displayName":displayName,"title":title,"subTitle":subTitle,"skipSetupScreen":skipSetupScreen,"cameraOn":cameraOn,"microphoneOn":microphoneOn});


    }
  else{

      print("Invalid groupId or meeting link");
return;
    }


  }

  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }


  /// Checks if [groupId] is a valid UUID (ACS Group Call Id).
  bool isValidGroupId(String groupId) {
    final uuidRegex = RegExp(
      r'^[0-9a-fA-F]{8}-'
      r'[0-9a-fA-F]{4}-'
      r'[0-9a-fA-F]{4}-'
      r'[0-9a-fA-F]{4}-'
      r'[0-9a-fA-F]{12}$',
    );
    return uuidRegex.hasMatch(groupId.trim());
  }

  /// Checks if [url] is a valid Microsoft Teams meeting link.
  bool isValidTeamsLink(String url) {
    if (url.isEmpty) return false;
    try {
      final uri = Uri.parse(url);
      if (!uri.isAbsolute) return false;
      // Teams meeting links usually look like:
      // https://teams.microsoft.com/l/meetup-join/...
      return uri.host.contains("teams.microsoft.com");
    } catch (_) {
      return false;
    }
  }

}
