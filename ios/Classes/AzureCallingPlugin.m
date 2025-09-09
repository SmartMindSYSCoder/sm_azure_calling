#import <Flutter/Flutter.h>
#import "sm_azure_calling-Swift.h"   // auto-generated header, matches your plugin name

@interface AzureCallingPlugin : NSObject<FlutterPlugin>
@end

@implementation AzureCallingPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
    [SwiftAzureCallingPlugin registerWithRegistrar:registrar];
}
@end
