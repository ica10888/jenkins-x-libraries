package com.example.springcloud.util

import groovy.json.JsonSlurper

class HealthChecker{

  def static boolean isAllUp (caller,url){
      def str
      return tryConnect(caller,url,1)
  }

  def static boolean tryConnect(caller,url,int i) {
      if(i <= 20){
          Thread.sleep(5000)
          caller.echo "第" + i + "次轮询，连接" + url
          try{
              def response = caller.httpRequest url
              caller.echo "Status:  " + response.status
              caller.echo "Content: " + response.content
              if (response.status == 200) {
                  def jsonSlurper
                  try {
                      jsonSlurper = new JsonSlurper().parseText(response.content)
                      if(jsonSlurper != null && "UP" == jsonSlurper.get("status")){
                        return true
                      }else {
                        return false
                      }
                  } catch (Exception e) {
                      caller.echo 'failed get jsonSlurper'
                  }

              }
          }catch (e) {
              caller.echo "连接失败"
              tryConnect(caller,url,i+1)
          }
      }else{
          caller.echo  "轮询失败"
          return false
      }
  }
}